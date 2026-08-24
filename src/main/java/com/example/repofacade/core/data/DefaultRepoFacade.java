package com.example.repofacade.core.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * RepoFacade 기본 구현. 매퍼마다 이 클래스가 하나씩 인스턴스화되어 빈으로 등록된다
 * — Spring Data의 SimpleJpaRepository와 같은 자리(공용 제네릭 구현체).
 */
public class DefaultRepoFacade<M> implements RepoFacade<M> {

    private final M mapper;
    private final ExceptionClassifier classifier;

    public DefaultRepoFacade(M mapper, ExceptionClassifier classifier) {
        this.mapper = mapper;
        this.classifier = classifier;
    }

    @Override
    public <R> DataResultWrapper<R> one(Function<M, R> query) {
        return call(() -> {
            R row = query.apply(mapper);
            return row == null
                    ? DataResultWrapper.fail(DataResultStatus.SQL_NO_DATA, "조회 결과 없음")
                    : DataResultWrapper.ok(row);
        });
    }

    @Override
    public <R> DataResultWrapper<List<R>> many(Function<M, List<R>> query) {
        return call(() -> {
            List<R> rows = query.apply(mapper);
            return DataResultWrapper.ok(rows == null ? List.<R>of() : rows);
        });
    }

    /* 인터페이스의 exec가 활성화되면 같이 해제한다.
    @Override
    public DataResultWrapper<Integer> exec(ToIntFunction<M> command) {
        return call(() -> DataResultWrapper.ok(command.applyAsInt(mapper)));
    }
    */

    private <T> DataResultWrapper<T> call(Supplier<DataResultWrapper<T>> body) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            return classifier.toResult(e);   // 분류 불가(버그성)면 여기서 다시 던져진다
        }
    }
}
