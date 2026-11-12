package com.example.repofacade.core.data;

/**
 * 파사드 호출 전후에 끼어드는 확장점. 빈으로 등록하면 모든 파사드 호출에 자동 적용된다 (여러 개면 {@code @Order} 순서).
 * 기술별 감시·로깅 장치가 쓴다 — 파사드는 누가 끼어드는지 모른다.
 *
 * {@link #before()}가 정상 종료한 훅의 {@link #after()}는 반드시 불린다 — 호출이 실패해도, 본문이나 다른 훅이 예외를 던져도.
 * {@code before()}가 예외를 던지면 그 훅의 {@code after()}는 불리지 않고, 뒤쪽 훅과 본문은 실행되지 않으며 예외는 그대로 나간다.
 */
public interface FacadeCallHook {

    void before();

    void after();
}
