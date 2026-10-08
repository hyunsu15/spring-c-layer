package com.example.repofacade.core.data.mybatis;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;

import com.example.repofacade.core.data.AbstractRepoFacadeRegistrar;

/**
 * MyBatis 매퍼 빈 정의(MapperFactoryBean)를 찾아 매퍼마다 파사드를 등록한다.
 * 매퍼 정의를 만드는 MapperScannerConfigurer가 BDRPP이므로 BFPP 단계에서 돈다 (상위 클래스 참고).
 */
public class MyBatisRepoFacadeRegistrar extends AbstractRepoFacadeRegistrar {

    static final String MAPPER_FACTORY_BEAN = "org.mybatis.spring.mapper.MapperFactoryBean";
    private static final String FACTORY_BEAN_OBJECT_TYPE = "factoryBeanObjectType";

    /** MapperFactoryBean 정의에서 매퍼 인터페이스를 추출한다. mybatis-spring 버전별 형태를 모두 시도. */
    @Override
    protected Class<?> resolveMapperType(BeanDefinition def, ClassLoader classLoader) {
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
}
