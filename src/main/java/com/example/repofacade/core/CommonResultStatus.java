package com.example.repofacade.core;

/**
 * 공통 상태. {@link #SUCCESS}는 시스템에 하나뿐인 성공 상태다 — 도메인 코드표에는 SUCCESS를 두지 않는다.
 * 성공 판단은 {@code r.ok()}로 하고, 상태를 직접 비교·생성할 때만 {@code CommonResultStatus.SUCCESS}를 쓴다.
 */
public enum CommonResultStatus implements ResultStatus {

    /** 유일한 성공 상태. */
    SUCCESS(0),

    /** 사유를 따로 구분하지 않는 일반 실패. */
    FAIL(1);

    private final int code;

    CommonResultStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
