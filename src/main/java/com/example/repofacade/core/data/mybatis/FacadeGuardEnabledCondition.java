package com.example.repofacade.core.data.mybatis;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 감시 스위치({@link MyBatisRepoFacadeConfiguration#GUARD_PROPERTY})가 true일 때만 참.
 * 스프링 부트의 @ConditionalOnProperty 대용 (이 프로젝트는 부트 없이 spring-context만 쓴다).
 */
class FacadeGuardEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment()
                .getProperty(MyBatisRepoFacadeConfiguration.GUARD_PROPERTY, Boolean.class, false);
    }
}
