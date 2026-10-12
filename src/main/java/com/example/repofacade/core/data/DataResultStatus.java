package com.example.repofacade.core.data;

import com.example.repofacade.core.ResultStatus;

/**
 * C의 반환 코드(rc)에 대응하는 상태 코드. 상수명은 C 레거시 코드명을 그대로 따른다.
 * 숫자 코드는 {@link #code()}로 노출되어 C 스타일 분기(if rc != 0)를 지원한다.
 *
 * {@link ResultStatus}를 구현하므로 데이터 실패를 일반 결과(ResultWrapper)로 rc 그대로 올려 보낼 수 있다.
 * 0~99는 공통·데이터 계층 대역이다 — 도메인 코드표는 100 이상을 쓴다.
 *
 * 성공은 두지 않는다 — 성공은 {@link com.example.repofacade.core.CommonResultStatus#SUCCESS} 하나뿐이다.
 */
public enum DataResultStatus implements ResultStatus {

    // 데이터 상태 — 흐름 분기 대상
    SQL_NO_DATA(10),   // 조회 결과 없음 (단건 null, 목록 비어 있음)
    SQLOVF(11),        // 단건 조회인데 2건 이상

    // 제약 위반 — 호출자가 응답으로 변환할 대상
    SQLDUP(20),        // 유니크 위반
    SQLNULL(21),       // NOT NULL 위반 (그 외 무결성 위반 포함)

    // 연결 장애 — 재시도·알람 대상
    SQLDISCON(30),     // DB 연결 단절/획득 실패

    // 그 외 — 재시도 무의미, 로그·알람 대상
    SYSTEM_ERROR(99);

    private final int code;

    DataResultStatus(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }
}
