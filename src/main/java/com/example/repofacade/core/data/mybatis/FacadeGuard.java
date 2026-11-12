package com.example.repofacade.core.data.mybatis;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.example.repofacade.core.data.FacadeCallHook;

/**
 * 파사드 밖 매퍼 호출 감시 장치. 두 가지 일을 한다.
 * <ul>
 *   <li>파사드 호출 전후 알림을 받아({@link FacadeCallHook}) "지금 파사드 안" 표시를 스레드에 켜고 끈다.</li>
 *   <li>SqlSessionFactory가 만들어지면({@link BeanPostProcessor}) 그 표시를 확인하는 {@link FacadeGuardInterceptor}를 붙인다.</li>
 * </ul>
 * 스위치({@link MyBatisRepoFacadeConfiguration#GUARD_PROPERTY})가 켜졌을 때만 빈으로 등록된다
 * ({@link FacadeGuardEnabledCondition}) — 꺼져 있으면 이 객체는 만들어지지 않고 파사드 호출 경로에 아무것도 끼지 않는다.
 *
 * 깊이로 센다 — 파사드에 넘긴 람다 안에서 다시 파사드를 부르는 경우에도 바깥 호출이 끝날 때까지 유지된다.
 */
class FacadeGuard implements FacadeCallHook, BeanPostProcessor {

    private final ThreadLocal<Integer> depth = new ThreadLocal<>();

    @Override
    public void before() {
        depth.set(depth() + 1);
    }

    @Override
    public void after() {
        int d = depth() - 1;
        if (d <= 0) {
            depth.remove();   // 스레드 풀 재사용 시 표시가 남지 않도록
        } else {
            depth.set(d);
        }
    }

    boolean insideFacade() {
        return depth() > 0;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SqlSessionFactory factory) {
            factory.getConfiguration().addInterceptor(new FacadeGuardInterceptor(this));
        }
        return bean;
    }

    private int depth() {
        Integer d = depth.get();
        return d == null ? 0 : d;
    }
}
