package com.example.repofacade.core.data.mybatis;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * MyBatis와 mybatis-spring이 클래스패스에 모두 있을 때만 참. 스프링 부트의 @ConditionalOnClass 대용
 * (이 프로젝트는 부트 없이 spring-context만 쓴다).
 */
class MyBatisPresentCondition implements Condition {

    private static final String MYBATIS = "org.apache.ibatis.session.SqlSessionFactory";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader cl = context.getClassLoader();
        return ClassUtils.isPresent(MYBATIS, cl)
                && ClassUtils.isPresent(MyBatisRepoFacadeRegistrar.MAPPER_FACTORY_BEAN, cl);
    }
}
