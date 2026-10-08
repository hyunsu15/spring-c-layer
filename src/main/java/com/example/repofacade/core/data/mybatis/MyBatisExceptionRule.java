package com.example.repofacade.core.data.mybatis;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.exceptions.TooManyResultsException;

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
        // 스프링 변환을 거치지 않은 MyBatis 예외 (SqlSession 직접 사용 등)
        if (e instanceof PersistenceException) {
            return DataResultStatus.SYSTEM_ERROR;
        }
        return null;
    }
}
