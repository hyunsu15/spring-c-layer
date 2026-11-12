package com.example.repofacade.core.data;

import java.util.function.Supplier;

/**
 * 데이터 접근 호출을 DataResultWrapper로 감싸는 파사드. 빈은 하나뿐이고, 데이터 접근 객체는 서비스가 평소처럼 주입받는다
 * — C에서 SQLW 래퍼가 SQL 함수를 실행하고 rc를 돌려주던 자리.
 *
 * <pre>
 * &#64;Autowired private RepoFacade facade;
 * &#64;Autowired private OrderMapper orderMapper;
 *
 * DataResultWrapper&lt;OrderRow&gt; r = facade.sel(() -&gt; orderMapper.selectById(id));
 * if (r.fail()) { ... }   // C 스타일 분기 — r.status().code() != 0 과 동일
 * </pre>
 *
 * 파사드 없이 직접 부르면 예외가 rc로 바뀌지 않는다. 이런 호출을 잡는 감시 장치는 기술별 연동이
 * {@link FacadeCallHook}으로 붙인다.
 */
public interface RepoFacade {

    /**
     * 조회. 단건이든 목록이든 이 메서드 하나로 부른다 — C의 {@code Sel…_SDB}처럼.
     * 결과가 null이거나 비어 있으면(컬렉션·맵·배열·Optional) SQL_NO_DATA(C의 NOT FOUND), 그 외는 성공이다.
     *
     * <p><b>단건인지 목록인지는 데이터 접근 객체의 반환 타입이 정한다.</b> 파사드는 결과를 받기만 한다.
     * 단건 타입으로 선언한 조회가 2건 이상이면 데이터 접근 기술이 던진 예외가 SQLOVF로 분류된다.
     * 그래서 C의 SELECT INTO(SQLOVF로 분기하던 자리)는 반드시 단건 타입으로 선언한다 — 목록 타입이면 2건도 정상이다.
     *
     * <p><b>0건이 정상인 호출부</b>는 {@code r.noData()}를 {@code r.fail()}보다 먼저 보고 따로 분기한다
     * ({@code valueOr(List.of())}는 연결 장애 같은 진짜 실패까지 빈 목록으로 삼키므로 쓰지 말 것).
     *
     * <p><b>null이 "0건"만 뜻하는지는 데이터 접근 기술에 달려 있다.</b>
     * 기술에 따라 "NULL 한 행"(0건 MAX/SUM, 값이 NULL인 단일 컬럼)도 null로 돌려주므로, 그런 조회는 SQL에서
     * NVL(COALESCE)로 감싸야 행이 있는데 SQL_NO_DATA가 되는 일을 막는다. 기술별 상세는 각 연동 패키지 문서를 볼 것.
     *
     * <p>비었는지 미리 알 수 없는 지연 결과(커서·스트림 등 컬렉션이 아닌 {@code Iterable}, {@code Iterator}, {@code Stream})는
     * 받지 않는다 — rc를 정할 수 없으므로 IllegalArgumentException(코드 버그로 보고 그대로 던진다).
     */
    <R> DataResultWrapper<R> sel(Supplier<R> query);

    /** 나중에 필요할때 사용하기. 아직은 필요없을듯?
     * 쓰기(INSERT/UPDATE/DELETE) — 영향 행 수 반환.
     *
     * 주의: 실패가 DataResultWrapper로 반환되면 예외가 트랜잭션 경계를 넘지 않으므로
     * {@code @Transactional}의 자동 롤백이 일어나지 않는다.
     * 여러 쓰기를 하나의 트랜잭션으로 묶는 경우의 정책은 README 참고.
     */
//    DataResultWrapper<Integer> exec(java.util.function.IntSupplier command);
}
