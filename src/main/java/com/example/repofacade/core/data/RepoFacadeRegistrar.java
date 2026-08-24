package com.example.repofacade.core.data;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * 매퍼 빈 정의(MapperFactoryBean)를 찾아 매퍼마다 DefaultRepoFacade 빈 정의를 등록한다.
 *
 * BeanDefinitionRegistryPostProcessor가 아니라 BeanFactoryPostProcessor인 이유:
 * 매퍼 정의를 만드는 MapperScannerConfigurer 자신이 BDRPP라서, 같은 단계에 끼어들면
 * 실행 순서가 등록 순서에 좌우된다. BFPP 단계는 모든 BDRPP가 끝난 뒤 실행이 보장된다.
 */
public class RepoFacadeRegistrar implements BeanFactoryPostProcessor {

    private static final String MAPPER_FACTORY_BEAN = "org.mybatis.spring.mapper.MapperFactoryBean";
    private static final String FACTORY_BEAN_OBJECT_TYPE = "factoryBeanObjectType";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw new IllegalStateException("BeanDefinitionRegistry가 아닌 팩토리에서는 파사드를 등록할 수 없습니다");
        }
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition def = beanFactory.getBeanDefinition(name);
            Class<?> mapperType = resolveMapperInterface(def, beanFactory.getBeanClassLoader());
            if (mapperType == null) {
                continue;
            }
            String facadeName = name + "Facade";
            if (!registry.containsBeanDefinition(facadeName)) {
                registry.registerBeanDefinition(facadeName, facadeDefinition(mapperType, beanFactory));
            }
        }
    }

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

    /** MapperFactoryBean 정의에서 매퍼 인터페이스를 추출한다. mybatis-spring 버전별 형태를 모두 시도. */
    private Class<?> resolveMapperInterface(BeanDefinition def, ClassLoader classLoader) {
        if (!MAPPER_FACTORY_BEAN.equals(def.getBeanClassName())) {
            return null;
        }
        Object candidate = def.getAttribute(FACTORY_BEAN_OBJECT_TYPE);
        if (candidate == null) {
            PropertyValue pv = def.getPropertyValues().getPropertyValue("mapperInterface");
            candidate = pv != null ? pv.getValue() : null;
        }
        if (candidate == null) {
            ConstructorArgumentValues.ValueHolder holder =
                    def.getConstructorArgumentValues().getArgumentValue(0, null);
            candidate = holder != null ? holder.getValue() : null;
        }
        return toClass(candidate, classLoader);
    }

    private Class<?> toClass(Object candidate, ClassLoader classLoader) {
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
