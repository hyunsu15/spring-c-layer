package com.example.repofacade.core.data.mybatis;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 파사드를 거치지 않은 매퍼 호출을 SQL 실행 직전에 거부한다. 파사드 밖 호출은 예외가 rc로 바뀌지 않아
 * "rc로 분기하는 C 스타일" 흐름이 깨지므로, 개발·테스트 단계에서 찾아내려는 용도다.
 *
 * 기본은 꺼져 있다 — {@link MyBatisRepoFacadeConfiguration#GUARD_PROPERTY}=true로 켜면 {@link FacadeGuard}가 붙인다.
 * 프레임워크가 직접 실행하는 SQL(페이징 건수 조회 등)도 파사드 밖 호출로 잡히므로, 켜기 전에 그런 경로가 있는지 확인할 것.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "queryCursor",
                args = {MappedStatement.class, Object.class, RowBounds.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
class FacadeGuardInterceptor implements Interceptor {

    private final FacadeGuard guard;

    FacadeGuardInterceptor(FacadeGuard guard) {
        this.guard = guard;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!guard.insideFacade()) {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            throw new IllegalStateException("파사드 밖 매퍼 호출: " + ms.getId()
                    + " — RepoFacade.one/many로 감싸서 호출할 것");
        }
        return invocation.proceed();
    }
}
