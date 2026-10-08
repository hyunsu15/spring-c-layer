package com.example.repofacade.core;

/**
 * C의 반환 코드(rc)에 대응하는 상태 코드. 계층 경계를 넘는 일반 결과({@link ResultWrapper})용이다.
 *
 * <p>코드표를 enum으로 못 박지 않고 인터페이스로 둔 이유는, 도메인마다 자기 코드표를 더할 수 있게
 * 하기 위함이다. 공통 계층은 이 타입만 알면 되고, 새 코드가 생겨도 공통 계층을 고칠 일이 없다.
 * <pre>{@code
 * public enum OrderResultStatus implements ResultStatus {
 *     NOT_SHIPPABLE(140), ALREADY_PAID(141);
 *     private final int code;
 *     OrderResultStatus(int code) { this.code = code; }
 *     public int code() { return code; }
 * }
 * }</pre>
 * enum은 {@code name()}을 이미 갖고 있으므로 {@code code()}만 구현하면 된다.
 *
 * <p><b>비교는 {@code ==}로 한다.</b> 규칙 두 가지만 지키면 항상 맞다.
 * <ul>
 *   <li><b>코드표는 enum으로만 만든다.</b> enum 상수는 JVM에 하나뿐이라 {@code ==}가 보장된다.</li>
 *   <li><b>코드표에 SUCCESS를 두지 않는다.</b> 성공은 {@link #SUCCESS} 하나뿐이다.
 *       (인터페이스 상수라 {@code OrderResultStatus.SUCCESS}로도 접근은 되지만 쓰지 말 것 — 코드표의 상수처럼 보여 헷갈린다.
 *       성공 판단은 {@code r.ok()}, 직접 부를 땐 {@code ResultStatus.SUCCESS}.)</li>
 * </ul>
 * {@code if (r.status() == OrderResultStatus.ALREADY_PAID)}, 성공 여부는 {@code r.ok()} / {@code r.fail()}.
 *
 * <p>rc 대역: 0~99는 공통·데이터 계층(SUCCESS=0, FAIL=1, DataResultStatus 10~99), 도메인 코드표는 100 이상.
 * 코드가 겹쳐도 {@code ==} 비교는 틀리지 않지만, C 쪽으로 내보내는 rc가 모호해지므로 대역을 지킨다.
 * 단, 성공 번호를 쓰는 실패 상태는 결과 객체가 거부한다 — C가 rc만 보고 성공으로 읽기 때문이다.
 */
public interface ResultStatus {

    /** 유일한 성공 상태. */
    ResultStatus SUCCESS = CommonResultStatus.SUCCESS;

    /** 사유를 따로 구분하지 않는 일반 실패. */
    ResultStatus FAIL = CommonResultStatus.FAIL;

    /** C 스타일 분기(if rc != 0)를 위한 숫자 코드. */
    int code();

    /** 로그·예외 메시지에 찍히는 이름. enum이 구현하면 상수명이 그대로 쓰인다. */
    String name();

    /**
     * 성공 여부는 번호가 아니라 정체로 판단한다 — 다른 코드표가 우연히 같은 번호를 써도 성공이 되지 않고,
     * 번호 체계가 바뀌어도 SUCCESS의 번호 한 줄만 고치면 된다.
     */
    default boolean ok() {
        return this == SUCCESS;
    }

    default boolean fail() {
        return !ok();
    }
}
