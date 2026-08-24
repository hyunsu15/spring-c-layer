package com.example.repofacade.core;

/**
 * C의 반환 코드(rc)에 대응하는 상태 코드. 계층 경계를 넘는 일반 결과({@link ResultWrapper})용이다.
 * 숫자 코드는 {@link #code()}로 노출되어 C 스타일 분기(if rc != 0)를 지원한다.
 *
 * 데이터 접근 실패는 코드표가 따로 있다 — core.data의 DataResultStatus 참고.
 */
public enum ResultStatus {
    SUCCESS(0),

    FAIL(1);

    private final int code;

    ResultStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
