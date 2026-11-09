package com.example.repofacade.core.data;

/**
 * 파사드 호출 전후에 끼어드는 확장점. 빈으로 등록하면 모든 파사드 호출에 자동 적용된다 (여러 개면 {@code @Order} 순서).
 * 기술별 감시·로깅 장치가 쓴다 — 파사드는 누가 끼어드는지 모른다.
 *
 * {@link #after()}는 호출이 실패하거나 예외가 나도 반드시 불린다.
 */
public interface FacadeCallHook {

    void before();

    void after();
}
