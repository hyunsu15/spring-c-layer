package com.example.repofacade.core.data;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

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
    public <R> DataResultWrapper<R> one(Supplier<R> query) {
        return call(() -> {
            R row = query.get();
            return row == null
                    ? DataResultWrapper.fail(DataResultStatus.SQL_NO_DATA, "조회 결과 없음")
                    : DataResultWrapper.ok(row);
        });
    }

    @Override
    public <R> DataResultWrapper<List<R>> many(Supplier<List<R>> query) {
        return call(() -> {
            List<R> rows = query.get();
            return rows == null || rows.isEmpty()
                    ? DataResultWrapper.fail(DataResultStatus.SQL_NO_DATA, "조회 결과 없음")
                    : DataResultWrapper.ok(rows);
        });
    }

    /* 인터페이스의 exec가 활성화되면 같이 해제한다.
    @Override
    public DataResultWrapper<Integer> exec(IntSupplier command) {
        return call(() -> DataResultWrapper.ok(command.getAsInt()));
    }
    */

    private <T> DataResultWrapper<T> call(Supplier<DataResultWrapper<T>> body) {
        for (FacadeCallHook hook : hooks) {
            hook.before();
        }
        try {
            return body.get();
        } catch (RuntimeException e) {
            return classifier.toResult(e);   // 분류 불가(버그성)면 여기서 다시 던져진다
        } finally {
            for (int i = hooks.size() - 1; i >= 0; i--) {   // 들어간 역순으로 나온다
                hooks.get(i).after();
            }
        }
    }
}
