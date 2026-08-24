package com.example.repofacade.core.data;

/**
 * 예외 대신 반환하는 결과 객체. C의 (rc, out 파라미터) 관례에 대응한다.
 *
 * 실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — rc 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 */
public record DataResultWrapper<T>(DataResultStatus status, T value, String detail) {

    public static <T> DataResultWrapper<T> ok(T value) {
        return new DataResultWrapper<>(DataResultStatus.SUCCESS, value, null);
    }

    public static <T> DataResultWrapper<T> fail(DataResultStatus status, String detail) {
        return new DataResultWrapper<>(status, null, detail);
    }

    public boolean ok() {
        return status == DataResultStatus.SUCCESS;
    }
    public boolean fail() {
        return status != DataResultStatus.SUCCESS;
    }


    @Override
    public T value() {
        if (!ok()) {
            throw new IllegalStateException(
                    "실패 결과(rc=" + status.code() + ", " + status + ")에서 value 접근: " + detail);
        }
        return value;
    }

    /** rc 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }
}
