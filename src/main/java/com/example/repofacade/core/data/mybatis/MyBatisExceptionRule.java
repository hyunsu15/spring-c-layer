package com.example.repofacade.core.data.mybatis;

import java.sql.SQLException;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.springframework.dao.DataAccessResourceFailureException;

import com.example.repofacade.core.data.DataExceptionRule;
import com.example.repofacade.core.data.DataResultStatus;

/** MyBatis 고유 예외 분류. 스프링 표준 예외로 바뀌지 않는 것들만 다룬다. */
public class MyBatisExceptionRule implements DataExceptionRule {

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
     * DB까지 가지 못한 MyBatis 내부 오류는 코드 버그다 — 매핑 누락(BindingException),
     * 파라미터·결과 매핑 오류(ReflectionException, TypeException 등).
     * 체인에 DB가 돌려준 오류(SQLException)나 연결 실패가 있으면 버그가 아니다 (일반 규칙이 분류).
     */
    @Override
    public boolean isCodeBug(RuntimeException e) {
        return DataExceptionRule.hasCause(e, PersistenceException.class)
                && !DataExceptionRule.hasCause(e, TooManyResultsException.class)
                && !DataExceptionRule.hasCause(e, SQLException.class)
                && !DataExceptionRule.hasCause(e, DataAccessResourceFailureException.class);
    }
}
