package com.example.repofacade.core.data;

import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;

/**
 * 기술별 예외 분류 규칙. {@link ExceptionClassifier}가 일반 규칙(스프링 DataAccessException 계층)보다
 * 먼저 적용한다 — 기술별 예외는 보통 스프링의 미분류 예외로 감싸여 오므로, 일반 규칙이 먼저 보면 SYSTEM_ERROR로 뭉개진다.
 *
 * 빈으로 등록하면 자동으로 적용된다. 여러 개면 {@code @Order} 순서.
 */
@FunctionalInterface
public interface DataExceptionRule {

    /** 분류할 수 있으면 상태를, 이 규칙의 대상이 아니면 null(다음 규칙으로)을 돌려준다. */
    DataResultStatus classify(RuntimeException e);

    /**
     * 고칠 대상(코드 버그)이면 true — rc로 바꾸지 않고 그대로 던진다. {@link #classify}보다 먼저 본다.
     * 기술별 내부 오류(매핑 누락 등)는 스프링 예외로 감싸여 와서 일반 규칙만으로는 버그인지 알 수 없다.
     */
    default boolean isCodeBug(RuntimeException e) {
        return false;
    }

    /**
     * 연결 획득 실패 또는 작업 중 연결 끊김. 타입이 아니라 cause 체인으로 본다
     * — 데이터 접근 기술에 따라 연결 획득 실패(CannotGetJdbcConnectionException)를 자기 예외로 감싸서 던진다.
     * 기술별 규칙도 쓴다: 연결 실패는 어느 단계(파라미터 세팅, 결과 읽기)에서 났든 코드 버그가 아니다.
     */
    static boolean isConnectionFailure(Throwable e) {
        return hasCause(e, DataAccessResourceFailureException.class)    // CannotGetJdbcConnectionException 포함
                || hasCause(e, RecoverableDataAccessException.class)
                || hasCause(e, SQLTransientConnectionException.class)     // 커넥션 풀 대기 시간 초과 등
                || hasCause(e, SQLNonTransientConnectionException.class)
                || hasCause(e, SQLRecoverableException.class);
    }

    /** cause 체인에 해당 타입이 있는지. 래핑된 예외 판별용. */
    static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
