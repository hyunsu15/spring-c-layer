package com.example.repofacade.core.data;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 매퍼 호출을 DataResultWrapper로 감싸는 파사드. 매퍼(M)마다 RepoFacade&lt;M&gt; 빈이 자동 등록된다.
 *
 * <pre>
 * // 주입: 제네릭 타입만으로 매퍼별 파사드가 구분된다
 * private final RepoFacade&lt;OrderMapper&gt; orders;
 *
 * DataResultWrapper&lt;OrderRow&gt; r = orders.one(m -&gt; m.selectById(id));
 * if (r.fail()) { ... }   // C 스타일 분기 — r.status().code() != 0 과 동일
 * </pre>
 */
public interface RepoFacade<M> {

    /** 단건 조회. 결과가 null이면 SQL_NO_DATA. */
    <R> DataResultWrapper<R> one(Function<M, R> query);

    /** 목록 조회. null은 빈 목록으로 취급. */
    <R> DataResultWrapper<List<R>> many(Function<M, List<R>> query);

    /** 나중에 필요할때 사용하기. 아직은 필요없을듯?
     * 쓰기(INSERT/UPDATE/DELETE) — 영향 행 수 반환.
     *
     * 주의: 실패가 DataResultWrapper로 반환되면 예외가 트랜잭션 경계를 넘지 않으므로
     * {@code @Transactional}의 자동 롤백이 일어나지 않는다.
     * 여러 쓰기를 하나의 트랜잭션으로 묶는 경우의 정책은 README 참고.
     */
//    DataResultWrapper<Integer> exec(ToIntFunction<M> command);
}
