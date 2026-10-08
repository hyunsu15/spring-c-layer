package com.example.repofacade.core;

/** 공통 상태 — {@link ResultStatus#SUCCESS} / {@link ResultStatus#FAIL}의 실체. 직접 쓰지 말고 그쪽 상수를 쓴다. */
enum CommonResultStatus implements ResultStatus {
    SUCCESS(0),

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
