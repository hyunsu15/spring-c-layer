package com.example.repofacade.core.data.mybatis;

import java.sql.SQLException;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.type.TypeException;

import com.example.repofacade.core.data.DataExceptionRule;
import com.example.repofacade.core.data.DataResultStatus;

/** MyBatis 고유 예외 분류. 스프링 표준 예외로 바뀌지 않는 것들만 다룬다. */
public class MyBatisExceptionRule implements DataExceptionRule {

    /**
     * MyBatis가 결과 컬럼을 읽다 실패했을 때 붙이는 문구 (BaseTypeHandler.getResult의 고정 영문 메시지).
     * mybatis-spring은 이 예외(ResultMapException)를 벗기고 안쪽 SQLException만 스프링 예외로 변환하므로 타입은 체인에
     * 남지 않고, 변환된 예외의 메시지 앞머리에만 남는다 — 그래서 이 경우만 메시지로 판별한다.
     */
    private static final String RESULT_MAPPING_MARKER = "Error attempting to get column";

    @Override
    public DataResultStatus classify(RuntimeException e) {
        // TooManyResults는 MyBatisSystemException 등에 감싸여 오므로 cause 체인으로 판별
        //해당값은 표준화 예외를 던지면 이거 필요없음. 해야하면 이거 필요함.
        if (DataExceptionRule.hasCause(e, TooManyResultsException.class)) {
            return DataResultStatus.SQLOVF;
        }
        return null;
    }

    /**
     * MyBatis 쪽 원인으로 난 오류는 코드 버그다.
     * <ul>
     *   <li>파라미터·결과 매핑 오류(TypeException, ResultMapException) — 드라이버가 SQLException을 냈더라도 원인은 매핑이다.
     *       예: null 파라미터에 jdbcType 누락("부적합한 열 유형: 1111"), 문자 컬럼을 숫자 필드로 받기.
     *       일반 규칙에 맡기면 SYSTEM_ERROR나 SQLNULL 같은 rc로 바뀌어 레거시 분기에 묻힌다.</li>
     *   <li>DB까지 가지 못한 MyBatis 내부 오류 — 매핑 누락(BindingException) 등.</li>
     * </ul>
     * 연결 실패와 TooManyResults는 어느 단계에서 났든 버그가 아니다 (일반 규칙과 {@link #classify}가 분류).
     */
    @Override
    public boolean isCodeBug(RuntimeException e) {
        if (DataExceptionRule.isConnectionFailure(e)
                || DataExceptionRule.hasCause(e, TooManyResultsException.class)) {
            return false;
        }
        if (DataExceptionRule.hasCause(e, TypeException.class)
                || DataExceptionRule.hasCause(e, ResultMapException.class)
                || isTranslatedResultMappingFailure(e)) {
            return true;
        }
        return DataExceptionRule.hasCause(e, PersistenceException.class)
                && !DataExceptionRule.hasCause(e, SQLException.class);
    }

    private static boolean isTranslatedResultMappingFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(RESULT_MAPPING_MARKER)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
