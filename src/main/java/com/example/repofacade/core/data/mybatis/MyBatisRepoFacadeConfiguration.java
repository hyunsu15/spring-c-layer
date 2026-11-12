package com.example.repofacade.core.data.mybatis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 연동 묶음. MyBatis 의존성이 있을 때만 활성화된다 — 없으면 이 패키지는 통째로 무시되고
 * core.data의 기술 무관 부분(파사드, ExceptionClassifier의 스프링 규칙 등)만 동작한다.
 *
 * 컴포넌트 스캔 범위에 core 패키지가 들어 있으면 자동으로 잡히고, 아니면 {@code @Import}로 가져온다.
 * 조건 판정은 클래스를 로드하기 전(메타데이터 단계)에 일어나므로, MyBatis가 없어도 이 클래스 때문에
 * NoClassDefFoundError가 나지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(MyBatisPresentCondition.class)
public class MyBatisRepoFacadeConfiguration {

    /** 파사드 밖 매퍼 호출 감시({@link FacadeGuard}, {@link FacadeGuardInterceptor}) 스위치. 기본 false. */
    public static final String GUARD_PROPERTY = "repo-facade.mybatis.guard.enabled";

    @Bean
    public MyBatisExceptionRule myBatisExceptionRule() {
        return new MyBatisExceptionRule();
    }

    /**
     * 파사드 밖 매퍼 호출 감시. 스위치가 켜졌을 때만 등록된다 — 꺼져 있으면 빈 자체가 없어서
     * 파사드의 훅 목록이 비고, 호출 경로에 아무것도 끼지 않는다.
     * 인터셉터를 빈으로 내놓지 않고 {@link FacadeGuard}가 SqlSessionFactory에 직접 붙이는 이유: 스프링 부트의 MyBatis 자동
     * 설정은 Interceptor 빈을 알아서 붙이므로, 빈으로 내놓으면 부트 환경에서는 두 번 붙는다.
     * BeanPostProcessor는 static @Bean으로 등록해야 설정 클래스 초기화보다 먼저 안전하게 뜬다.
     */
    @Bean
    @Conditional(FacadeGuardEnabledCondition.class)
    static FacadeGuard facadeGuard() {
        return new FacadeGuard();
    }
}
