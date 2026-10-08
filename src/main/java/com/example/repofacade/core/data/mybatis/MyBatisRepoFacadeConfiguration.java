package com.example.repofacade.core.data.mybatis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 연동 묶음. MyBatis 의존성이 있을 때만 활성화된다 — 없으면 이 패키지는 통째로 무시되고
 * core.data의 기술 무관 부분(ExceptionClassifier의 스프링 규칙 등)만 동작한다.
 *
 * 컴포넌트 스캔 범위에 core 패키지가 들어 있으면 자동으로 잡히고, 아니면 {@code @Import}로 가져온다.
 * 조건 판정은 클래스를 로드하기 전(메타데이터 단계)에 일어나므로, MyBatis가 없어도 이 클래스 때문에
 * NoClassDefFoundError가 나지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(MyBatisPresentCondition.class)
public class MyBatisRepoFacadeConfiguration {

    /** BeanFactoryPostProcessor는 static @Bean으로 등록해야 설정 클래스 초기화보다 먼저 안전하게 뜬다. */
    @Bean
    public static MyBatisRepoFacadeRegistrar myBatisRepoFacadeRegistrar() {
        return new MyBatisRepoFacadeRegistrar();
    }

    @Bean
    public MyBatisExceptionRule myBatisExceptionRule() {
        return new MyBatisExceptionRule();
    }
}
