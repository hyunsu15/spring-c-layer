package com.example.repofacade.core;

import java.util.Objects;

/**
 * 예외 대신 반환하는 결과 객체. C의 (rc, out 파라미터) 관례에 대응한다.
 * common -> 중간서비스 -> 서비스처럼 계층을 거칠 때, 각 단계가 상태만 확인하고
 * 실패는 그대로 위로 전달한다 (try/catch 대신).
 *
 * 데이터 접근 결과는 core.data의 DataResultWrapper를 쓴다. DataResultStatus도 ResultStatus이므로
 * 데이터 실패는 {@code ResultWrapper.fail(r.status(), r.detail())}로 rc 그대로 올려 보낼 수 있다.
 *
 * 실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — 상태 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 */
public record ResultWrapper<T>(ResultStatus status, T value, String detail) {

    public ResultWrapper {
        Objects.requireNonNull(status, "status");
        // 실패인데 번호가 성공 번호면 C 쪽은 rc만 보고 성공으로 읽는다 — 코드표의 번호 실수를 첫 사용에서 잡는다
        if (!status.ok() && status.code() == ResultStatus.SUCCESS.code()) {
            throw new IllegalArgumentException("실패 상태가 성공 번호(rc=" + status.code() + ")를 쓴다: "
                    + status.getClass().getName() + "." + status.name());
        }
    }

    public static <T> ResultWrapper<T> ok(T value) {
        return new ResultWrapper<>(ResultStatus.SUCCESS, value, null);
    }

    /** 사유를 구분하지 않는 일반 실패. */
    public static <T> ResultWrapper<T> fail(String detail) {
        return new ResultWrapper<>(ResultStatus.FAIL, null, detail);
    }

    /** 도메인 코드표의 상태로 실패. */
    public static <T> ResultWrapper<T> fail(ResultStatus status, String detail) {
        if (status.ok()) {
            throw new IllegalArgumentException("성공 상태로 실패 결과를 만들 수 없다: " + status.name());
        }
        return new ResultWrapper<>(status, null, detail);
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

    /** 상태 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }
}
