package com.example.repofacade.core.data;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.BaseStream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * RepoFacade 기본 구현. 애플리케이션 전체에 빈 하나 — 데이터 접근 객체를 들고 있지 않고, 넘겨받은 호출을 실행만 한다.
 */
@Component
public class DefaultRepoFacade implements RepoFacade {

    private final ExceptionClassifier classifier;
    private final List<FacadeCallHook> hooks;

    public DefaultRepoFacade(ExceptionClassifier classifier, ObjectProvider<FacadeCallHook> hooks) {
        this.classifier = classifier;
        this.hooks = hooks.orderedStream().toList();
    }

    @Override
    public <R> DataResultWrapper<R> sel(Supplier<R> query) {
        return call(() -> {
            R result = query.get();
            return isEmpty(result)
                    ? DataResultWrapper.fail(DataResultStatus.SQL_NO_DATA, "조회 결과 없음")
                    : DataResultWrapper.ok(result);
        });
    }

    /* 인터페이스의 exec가 활성화되면 같이 해제한다.
    @Override
    public DataResultWrapper<Integer> exec(java.util.function.IntSupplier command) {
        return call(() -> DataResultWrapper.ok(command.getAsInt()));
    }
    */

    /**
     * 0건(C의 NOT FOUND)인가. 단건은 null, 목록·맵·배열·Optional은 비어 있을 때.
     * 비었는지 미리 알 수 없는 지연 결과는 rc를 정할 수 없으므로 거부한다 — 이 예외는 분류 대상이 아니라 그대로 던져진다.
     */
    private static boolean isEmpty(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (result instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        if (result instanceof Optional<?> o) {
            return o.isEmpty();
        }
        if (result.getClass().isArray()) {
            return Array.getLength(result) == 0;
        }
        if (result instanceof Iterable<?> || result instanceof Iterator<?> || result instanceof BaseStream<?, ?>) {
            throw new IllegalArgumentException("비었는지 알 수 없는 지연 결과(" + result.getClass().getName()
                    + ")는 sel로 받을 수 없다 — List로 받을 것");
        }
        return false;
    }

    private <T> DataResultWrapper<T> call(Supplier<DataResultWrapper<T>> body) {
        return call(0, body);
    }

    /**
     * index번째 훅부터 차례로 들어갔다가 역순으로 나온다. 훅마다 자기 finally를 가지므로, before()가 끝난 훅은
     * 뒤쪽 훅의 before()/after()나 본문이 예외를 던져도 after()가 반드시 불린다.
     * 훅이 던진 예외는 분류하지 않는다 — rc로 바꾸는 대상은 본문(데이터 접근)의 예외뿐이다.
     */
    private <T> DataResultWrapper<T> call(int index, Supplier<DataResultWrapper<T>> body) {
        if (index == hooks.size()) {
            try {
                return body.get();
            } catch (RuntimeException e) {
                return classifier.toResult(e);   // 분류 불가(버그성)면 여기서 다시 던져진다
            }
        }
        FacadeCallHook hook = hooks.get(index);
        hook.before();
        try {
            return call(index + 1, body);
        } finally {
            hook.after();
        }
    }
}
