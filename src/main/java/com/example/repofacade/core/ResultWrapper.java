package com.example.repofacade.core;

/**
 * 예외 대신 반환하는 결과 객체. C의 (rc, out 파라미터) 관례에 대응한다.
 * common -> 중간서비스 -> 서비스처럼 계층을 거칠 때, 각 단계가 상태만 확인하고
 * 실패는 그대로 위로 전달한다 (try/catch 대신).
 *
 * 데이터 접근 결과는 core.data의 DataResultWrapper를 쓴다 — 상태 코드표가 다르다.
 *
 * 실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — 상태 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 */
public record ResultWrapper<T>(ResultStatus status, T value, String detail) {

    public static <T> ResultWrapper<T> ok(T value) {
        return new ResultWrapper<>(ResultStatus.SUCCESS, value, null);
    }

    public static <T> ResultWrapper<T> fail(String detail) {
        return new ResultWrapper<>(ResultStatus.FAIL, null, detail);
    }

    public boolean ok() {
        return status == ResultStatus.SUCCESS;
    }
    public boolean fail() {
        return status != ResultStatus.SUCCESS;
    }
P@
    @Override
    public T value() {
        if (!ok()) {
            throw new IllegalStateException(
                    "실패 결과(rc=" + status.code() + ", " + status + ")에서 value 접근: " + detail);
        }
        return value;
    }

    /** 상태 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }
}
