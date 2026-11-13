package com.example.repofacade.core;

import java.util.Objects;

/**
 * 예외 대신 반환하는 결과 객체. C의 (rc, out 파라미터) 관례에 대응한다.
 * common -> 중간서비스 -> 서비스처럼 계층을 거칠 때, 각 단계가 상태만 확인하고
 * 실패는 그대로 위로 전달한다 (try/catch 대신).
 *
 * 데이터 접근 결과는 core.data의 DataResultWrapper(파사드만 생성)를 쓰고, 중간모듈은 이 타입으로 주고받는다.
 *
 * <p><b>실패 처리 규칙 — 이름으로 갈린다.</b>
 * <ul>
 *   <li><b>받은 실패를 넘길 때: {@code r.propagate()}</b> — 받은 결과가 DataResultWrapper든 ResultWrapper든 같다.
 *       rc·detail을 그대로 유지하고 값 타입만 호출부에 맞춘다.</li>
 *   <li><b>새 실패를 만들 때: {@code ResultWrapper.fail(...)}</b> — 이 계층에서 판단한 실패(도메인 코드표 상태).</li>
 * </ul>
 * <pre>{@code
 * ResultWrapper<CommonData> r = commonService.call(req);
 * if (r.fail()) {
 *     return r.propagate();                                       // 받은 실패: rc 그대로
 * }
 * if (r.value().isPaid()) {
 *     return ResultWrapper.fail(OrderResultStatus.ALREADY_PAID, "id=" + id);   // 새 실패
 * }
 * }</pre>
 *
 * 실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — 상태 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 *
 * <p>detail은 사람용 실패 설명(C의 sqlw_msg), cause는 rc로 바뀐 원래 예외(없으면 null)다.
 * propagate는 둘 다 그대로 넘기므로, 최상위 서비스가 프레임워크 예외로 바꿀 때 원인을 이을 수 있다
 * ({@code throw new BizException("8000", r.cause())} — 사내 프레임워크 예외 자리).
 */
public record ResultWrapper<T>(ResultStatus status, T value, String detail, Throwable cause) {

    public ResultWrapper {
        Objects.requireNonNull(status, "status");
        // 실패인데 번호가 성공 번호면 C 쪽은 rc만 보고 성공으로 읽는다 — 코드표의 번호 실수를 첫 사용에서 잡는다
        if (!status.ok() && status.code() == CommonResultStatus.SUCCESS.code()) {
            throw new IllegalArgumentException("실패 상태가 성공 번호(rc=" + status.code() + ")를 쓴다: "
                    + status.getClass().getName() + "." + status.name());
        }
        if (status.ok() && cause != null) {
            throw new IllegalArgumentException("성공 결과에 원인 예외를 담을 수 없다: " + cause);
        }
    }

    public static <T> ResultWrapper<T> ok(T value) {
        return new ResultWrapper<>(CommonResultStatus.SUCCESS, value, null, null);
    }

    /** 사유를 구분하지 않는 일반 실패. */
    public static <T> ResultWrapper<T> fail(String detail) {
        return new ResultWrapper<>(CommonResultStatus.FAIL, null, detail, null);
    }

    /**
     * 실패를 rc·detail 그대로 위로 넘긴다 — {@code if (r.fail()) return r.propagate();}.
     * 반환 값 타입은 호출부에 맞춰진다. DataResultWrapper의 propagate()와 같은 규칙이다.
     * 성공 결과에 쓰면 값이 사라지므로 IllegalStateException — 성공은 {@code ResultWrapper.ok(r.value())}로 넘긴다.
     */
    public <U> ResultWrapper<U> propagate() {
        if (ok()) {
            throw new IllegalStateException("성공 결과는 propagate할 수 없다 — ResultWrapper.ok(r.value())를 쓸 것");
        }
        return new ResultWrapper<>(status, null, detail, cause);
    }

    /**
     * 이 계층에서 판단한 새 실패를 도메인 코드표의 상태로 만든다.
     * 받은 실패를 넘길 때는 이것 대신 {@link #propagate()}를 쓴다.
     */
    public static <T> ResultWrapper<T> fail(ResultStatus status, String detail) {
        return fail(status, detail, null);
    }

    /**
     * 새 실패를 만들되 원인 예외를 잇는다 — 이 계층이 예외를 잡아 rc로 바꾸는 자리(예: 기존 예외 기반 모듈을 감쌀 때).
     * 원인을 버리면 상위에서 스택·SQLCODE를 볼 수 없게 된다.
     */
    public static <T> ResultWrapper<T> fail(ResultStatus status, String detail, Throwable cause) {
        if (status.ok()) {
            throw new IllegalArgumentException("성공 상태로 실패 결과를 만들 수 없다: " + status.name());
        }
        return new ResultWrapper<>(status, null, detail, cause);
    }

    public boolean ok() {
        return status.ok();
    }
    public boolean fail() {
        return status.fail();
    }

    /** 0건인가 — 상태가 0건으로 선언된 경우만 true ({@link ResultStatus#noData()}). 0건이 정상인 호출부는 fail()보다 먼저 본다. */
    public boolean noData() {
        return status.noData();
    }

    @Override
    public T value() {
        if (!ok()) {
            throw new IllegalStateException(
                    "실패 결과(rc=" + status.code() + ", " + status.name() + ")에서 value 접근: " + detail, cause);
        }
        return value;
    }

    /** 상태 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }
}
