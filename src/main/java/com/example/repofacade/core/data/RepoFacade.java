package com.example.repofacade.core.data;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 데이터 접근 호출을 DataResultWrapper로 감싸는 파사드. 빈은 하나뿐이고, 데이터 접근 객체는 서비스가 평소처럼 주입받는다
 * — C에서 SQLW 래퍼가 SQL 함수를 실행하고 rc를 돌려주던 자리.
 *
 * <pre>
 * &#64;Autowired private RepoFacade facade;
 * &#64;Autowired private OrderMapper orderMapper;
 *
 * DataResultWrapper&lt;OrderRow&gt; r = facade.one(() -&gt; orderMapper.selectById(id));
 * if (r.fail()) { ... }   // C 스타일 분기 — r.status().code() != 0 과 동일
 * </pre>
 *
 * 파사드 없이 직접 부르면 예외가 rc로 바뀌지 않는다. 이런 호출을 잡는 감시 장치는 기술별 연동이
 * {@link FacadeCallHook}으로 붙인다.
 */
public interface RepoFacade {

    /**
     * 단건 조회. 결과가 null이면 SQL_NO_DATA.
     *
     * <p><b>주의 — null이 "0건"만 뜻하는지는 데이터 접근 기술에 달려 있다.</b>
     * 기술에 따라 "NULL 한 행"(0건 MAX/SUM, 값이 NULL인 단일 컬럼)도 null로 돌려주므로, 그런 조회는 SQL에서
     * NVL(COALESCE)로 감싸야 행이 있는데 SQL_NO_DATA가 되는 일을 막는다. 기술별 상세는 각 연동 패키지 문서를 볼 것.
     */
    <R> DataResultWrapper<R> one(Supplier<R> query);

    /**
     * 목록 조회. 결과가 비었거나 null이면 SQL_NO_DATA — 단건 조회와 같은 규칙(C의 NOT FOUND).
     * 0건도 정상인 호출부는 {@code r.status() == DataResultStatus.SQL_NO_DATA}로 따로 분기한다
     * ({@code valueOr(List.of())}는 연결 장애 같은 진짜 실패까지 빈 목록으로 삼키므로 쓰지 말 것).
     */
    <R> DataResultWrapper<List<R>> many(Supplier<List<R>> query);

    /** 나중에 필요할때 사용하기. 아직은 필요없을듯?
     * 쓰기(INSERT/UPDATE/DELETE) — 영향 행 수 반환.
     *
     * 주의: 실패가 DataResultWrapper로 반환되면 예외가 트랜잭션 경계를 넘지 않으므로
     * {@code @Transactional}의 자동 롤백이 일어나지 않는다.
     * 여러 쓰기를 하나의 트랜잭션으로 묶는 경우의 정책은 README 참고.
     */
//    DataResultWrapper<Integer> exec(IntSupplier command);
}
