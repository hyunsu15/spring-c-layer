package com.example.repofacade.core.data;

import java.util.Objects;

import com.example.repofacade.core.ResultStatus;

/**
 * 예외 대신 반환하는 결과 객체. C의 (rc, out 파라미터) 관례에 대응한다.
 *
 * 상태는 성공이면 {@link ResultStatus#SUCCESS}, 실패면 {@link DataResultStatus} 중 하나다.
 * 비교는 {@code r.status() == DataResultStatus.SQLDUP}처럼 {@code ==}로 한다.
 *
 * 실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — rc 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 */
public record DataResultWrapper<T>(ResultStatus status, T value, String detail) {

    public DataResultWrapper {
        Objects.requireNonNull(status, "status");
        // 실패인데 번호가 성공 번호면 C 쪽은 rc만 보고 성공으로 읽는다 — 코드표의 번호 실수를 첫 사용에서 잡는다
        if (!status.ok() && status.code() == ResultStatus.SUCCESS.code()) {
            throw new IllegalArgumentException("실패 상태가 성공 번호(rc=" + status.code() + ")를 쓴다: "
                    + status.getClass().getName() + "." + status.name());
        }
    }

    public static <T> DataResultWrapper<T> ok(T value) {
        return new DataResultWrapper<>(ResultStatus.SUCCESS, value, null);
    }

    public static <T> DataResultWrapper<T> fail(DataResultStatus status, String detail) {
        return new DataResultWrapper<>(status, null, detail);
    }

    /** 다른 DataResultWrapper의 실패를 rc 그대로 전파할 때. */
    public static <T> DataResultWrapper<T> fail(ResultStatus status, String detail) {
        if (status.ok()) {
            throw new IllegalArgumentException("성공 상태로 실패 결과를 만들 수 없다: " + status.name());
        }
        return new DataResultWrapper<>(status, null, detail);
    }

    public boolean ok() {
        return status.ok();
    }
    public boolean fail() {
        return status.fail();
    }

    @Override
    public T value() {
        if (!ok()) {
            throw new IllegalStateException(
                    "실패 결과(rc=" + status.code() + ", " + status.name() + ")에서 value 접근: " + detail);
        }
        return value;
    }

    /** rc 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }
}
