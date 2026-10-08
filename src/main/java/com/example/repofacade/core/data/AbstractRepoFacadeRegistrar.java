package com.example.repofacade.core.data;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * 매퍼 빈 정의를 찾아 매퍼마다 DefaultRepoFacade 빈 정의를 등록하는 등록기의 공통 뼈대.
 * "어떤 빈 정의가 매퍼인가"만 기술별로 다르므로 {@link #resolveMapperType}만 구현하면 된다
 * (MyBatis 구현은 core.data.mybatis.MyBatisRepoFacadeRegistrar).
 *
 * BeanDefinitionRegistryPostProcessor가 아니라 BeanFactoryPostProcessor인 이유:
 * 매퍼 정의를 만드는 쪽(MyBatis의 MapperScannerConfigurer 등)이 보통 BDRPP라서, 같은 단계에 끼어들면
 * 실행 순서가 등록 순서에 좌우된다. BFPP 단계는 모든 BDRPP가 끝난 뒤 실행이 보장된다.
 */
public abstract class AbstractRepoFacadeRegistrar implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw new IllegalStateException("BeanDefinitionRegistry가 아닌 팩토리에서는 파사드를 등록할 수 없습니다");
        }
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition def = beanFactory.getBeanDefinition(name);
            Class<?> mapperType = resolveMapperType(def, beanFactory.getBeanClassLoader());
            if (mapperType == null) {
                continue;
            }
            String facadeName = name + "Facade";
            if (!registry.containsBeanDefinition(facadeName)) {
                registry.registerBeanDefinition(facadeName, facadeDefinition(mapperType, beanFactory));
            }
        }
    }

    /** 이 빈 정의가 매퍼라면 매퍼 타입을, 아니면 null을 돌려준다. */
    protected abstract Class<?> resolveMapperType(BeanDefinition def, ClassLoader classLoader);

    private <M> RootBeanDefinition facadeDefinition(Class<M> mapperType,
            ConfigurableListableBeanFactory beanFactory) {
        RootBeanDefinition def = new RootBeanDefinition(DefaultRepoFacade.class);
        // 제네릭까지 실어야 RepoFacade<OrderMapper> 주입 지점에 타입으로 매칭된다
        def.setTargetType(ResolvableType.forClassWithGenerics(DefaultRepoFacade.class, mapperType));
        def.setInstanceSupplier(() -> new DefaultRepoFacade<>(
                beanFactory.getBean(mapperType),
                beanFactory.getBean(ExceptionClassifier.class)));
        return def;
    }

    /** 빈 정의에 실린 타입 값(Class 또는 클래스명)을 Class로 바꾼다. */
    protected Class<?> toClass(Object candidate, ClassLoader classLoader) {
        if (candidate instanceof Class<?> c) {
            return c;
        }
        if (candidate instanceof String s) {
            try {
                return ClassUtils.forName(s, classLoader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("매퍼 인터페이스 로드 실패: " + s, e);
            }
        }
        return null;
    }
}
