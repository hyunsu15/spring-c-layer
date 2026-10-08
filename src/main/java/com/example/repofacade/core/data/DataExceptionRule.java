package com.example.repofacade.core.data;

/**
 * 기술별(MyBatis 등) 예외 분류 규칙. {@link ExceptionClassifier}가 일반 규칙(스프링 DataAccessException 계층)보다
 * 먼저 적용한다 — 기술별 예외는 보통 스프링의 미분류 예외로 감싸여 오므로, 일반 규칙이 먼저 보면 SYSTEM_ERROR로 뭉개진다.
 *
 * 빈으로 등록하면 자동으로 적용된다. 여러 개면 {@code @Order} 순서.
 */
@FunctionalInterface
public interface DataExceptionRule {

    /** 분류할 수 있으면 상태를, 이 규칙의 대상이 아니면 null(다음 규칙으로)을 돌려준다. */
    DataResultStatus classify(RuntimeException e);

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
