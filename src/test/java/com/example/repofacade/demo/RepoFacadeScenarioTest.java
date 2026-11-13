package com.example.repofacade.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.MyBatisSystemException;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import com.example.repofacade.core.CommonResultStatus;
import com.example.repofacade.core.ResultStatus;
import com.example.repofacade.core.ResultWrapper;
import com.example.repofacade.core.data.DataExceptionRule;
import com.example.repofacade.core.data.DataResultStatus;
import com.example.repofacade.core.data.DataResultWrapper;
import com.example.repofacade.core.data.DefaultRepoFacade;
import com.example.repofacade.core.data.ExceptionClassifier;
import com.example.repofacade.core.data.FacadeCallHook;
import com.example.repofacade.core.data.RepoFacade;
import com.example.repofacade.core.data.mybatis.MyBatisRepoFacadeConfiguration;

/**
 * 파사드가 실제로 동작하는지 시나리오별로 검증한다 (H2 + MyBatis, 공용 컨텍스트는 한 번만 띄운다).
 * 사용 예시로도 읽히도록 서비스 흐름(OrderService)과 파사드 직접 호출을 함께 둔다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepoFacadeScenarioTest {

    private AnnotationConfigApplicationContext ctx;
    private OrderService service;
    private RepoFacade facade;
    private OrderMapper orderMapper;

    @BeforeAll
    void startContext() {
        ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        service = ctx.getBean(OrderService.class);
        facade = ctx.getBean(RepoFacade.class);
        orderMapper = ctx.getBean(OrderMapper.class);
    }

    @AfterAll
    void closeContext() {
        ctx.close();
    }

    @Nested
    @DisplayName("조회 결과 (sel — 단건·목록은 매퍼 반환 타입이 정한다)")
    class Query {

        @Test
        @DisplayName("단건 조회 성공 — rc=0, 값 그대로")
        void oneFound() {
            DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(1));
            assertEquals(0, r.status().code());
            assertEquals("kim", r.value().getCustomerName());
            assertEquals(0, new BigDecimal("10000.00").compareTo(r.value().getAmount()));
        }

        @Test
        @DisplayName("없는 행은 예외 없이 SQL_NO_DATA(rc=10)")
        void oneMissing() {
            DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(999));
            assertTrue(r.fail());
            assertSame(DataResultStatus.SQL_NO_DATA, r.status());
        }

        @Test
        @DisplayName("selectOne인데 2건 — 감싸진 TooManyResults도 SQLOVF(rc=11)")
        void oneTooMany() {
            assertSame(DataResultStatus.SQLOVF, facade.sel(() -> orderMapper.selectByCustomer("lee")).status());
        }

        @Test
        @DisplayName("2건 이상 — MyBatis 형태(TooManyResultsException)는 날것이든 감싸였든 SQLOVF")
        void tooManyMyBatisForms() {
            assertSame(DataResultStatus.SQLOVF, statusOfThrown(new TooManyResultsException("날것")));
            assertSame(DataResultStatus.SQLOVF,
                    statusOfThrown(new MyBatisSystemException(new TooManyResultsException("기본 번역기"))));
        }

        @Test
        @DisplayName("2건 이상 — 스프링 표준 형태(IncorrectResultSizeDataAccessException)도 SQLOVF, 원래 예외 유무와 무관")
        void tooManySpringStandardForms() {
            // 예외 표준화 번역기를 쓴 경우 — 원래 예외를 cause로 남기든 버리든
            assertSame(DataResultStatus.SQLOVF, statusOfThrown(
                    new IncorrectResultSizeDataAccessException("표준화", 1, new TooManyResultsException("원래"))));
            assertSame(DataResultStatus.SQLOVF, statusOfThrown(new IncorrectResultSizeDataAccessException(1, 2)));
        }

        @Test
        @DisplayName("0건 표준 예외(EmptyResult)는 IncorrectResultSize의 하위 타입이지만 SQLOVF가 아니라 SQL_NO_DATA")
        void emptyResultIsNoDataNotOverflow() {
            assertSame(DataResultStatus.SQL_NO_DATA, statusOfThrown(new EmptyResultDataAccessException(1)));
        }

        @Test
        @DisplayName("같은 2건이라도 매퍼를 목록 타입으로 선언하면 정상 — SQLOVF는 단건 타입 선언에서만 난다")
        void listTypeTakesMultipleRows() {
            DataResultWrapper<List<OrderRow>> r = facade.sel(() -> orderMapper.selectListByCustomer("lee"));
            assertTrue(r.ok());
            assertEquals(2, r.value().size());
        }

        @Test
        @DisplayName("Optional 반환 — 있으면 성공, 0건(empty)이면 SQL_NO_DATA")
        void optionalResult() {
            assertTrue(facade.sel(() -> orderMapper.selectOptionalById(1)).ok());
            assertSame(DataResultStatus.SQL_NO_DATA, facade.sel(() -> orderMapper.selectOptionalById(999)).status());
        }

        @Test
        @DisplayName("배열 반환 — 있으면 성공, 빈 배열이면 SQL_NO_DATA")
        void arrayResult() {
            assertEquals(2, facade.sel(() -> orderMapper.selectArrayByStatus("NEW")).value().length);
            assertSame(DataResultStatus.SQL_NO_DATA, facade.sel(() -> orderMapper.selectArrayByStatus("CANCELLED")).status());
        }

        @Test
        @DisplayName("커서 반환은 비었는지 알 수 없어 rc로 바꾸지 않고 거부(코드 버그)")
        void cursorRejected() {
            assertThrows(IllegalArgumentException.class, () -> facade.sel(() -> orderMapper.selectAllAsCursor()));
        }

        @Test
        @DisplayName("목록 조회 3건")
        void manyFound() {
            DataResultWrapper<List<OrderRow>> r = facade.sel(() -> orderMapper.selectAll());
            assertTrue(r.ok());
            assertEquals(3, r.value().size());
        }

        @Test
        @DisplayName("빈 목록도 SQL_NO_DATA — 단건 조회와 같은 규칙(C의 NOT FOUND)")
        void manyEmpty() {
            assertSame(DataResultStatus.SQL_NO_DATA, facade.sel(() -> orderMapper.selectByStatus("CANCELLED")).status());
        }

        @Test
        @DisplayName("감싸지 않은 0건 MAX()는 SQL_NO_DATA — 알려진 한계 (core.data.mybatis 패키지 문서 참고)")
        void aggregateWithoutNvl() {
            assertSame(DataResultStatus.SQL_NO_DATA,
                    facade.sel(() -> orderMapper.selectMaxAmountByStatus("CANCELLED")).status());
        }

        @Test
        @DisplayName("NVL(COALESCE)로 감싼 0건 MAX()는 성공(0)")
        void aggregateWithNvl() {
            DataResultWrapper<BigDecimal> r = facade.sel(() -> orderMapper.selectMaxAmountByStatusOrZero("CANCELLED"));
            assertTrue(r.ok());
            assertEquals(0, r.value().signum());
        }
    }

    @Nested
    @DisplayName("실패 분류 — 데이터·장애는 rc, 코드 버그는 예외")
    class Classification {

        @Test
        @DisplayName("테이블명 오타는 rc로 바꾸지 않고 그대로 던진다")
        void badSqlThrows() {
            assertThrows(InvalidDataAccessResourceUsageException.class,
                    () -> facade.sel(() -> orderMapper.selectFromTypoTable(1)));
        }

        @Test
        @DisplayName("SQL 매핑 누락은 rc로 바꾸지 않고 그대로 던진다")
        void missingMappingThrows() {
            assertThrows(BindingException.class, () -> facade.sel(() -> orderMapper.selectWithoutSql(1)));
        }

        @Test
        @DisplayName("결과 매핑 오류(문자 컬럼을 숫자로)는 드라이버가 SQLException을 냈어도 rc(SQLNULL)로 바꾸지 않고 던진다")
        void resultMappingErrorThrows() {
            assertThrows(DataAccessException.class, () -> facade.sel(() -> orderMapper.selectNameAsNumber(1)));
        }

        @Test
        @DisplayName("파라미터 매핑 오류는 드라이버가 SQLException을 냈어도 rc(SYSTEM_ERROR)로 바꾸지 않고 던진다")
        void paramMappingErrorThrows() {
            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> facade.sel(() -> orderMapper.selectIdWithFailingParam("kim")));
            assertTrue(DataExceptionRule.hasCause(e, TypeException.class), e.toString());
        }

        @Test
        @DisplayName("파라미터 세팅 도중 연결이 끊긴 것은 매핑 오류가 아니다 — SQLDISCON(rc=30)")
        void disconnectWhileBindingIsDiscon() {
            assertSame(DataResultStatus.SQLDISCON,
                    facade.sel(() -> orderMapper.selectIdWithFailingParam(FailingParamTypeHandler.DISCONNECTED)).status());
        }

        @Test
        @DisplayName("DB 접속 불가는 SQLDISCON(rc=30) — MyBatis가 MyBatisSystemException으로 감싸 와도")
        void deadDbIsDiscon() throws Exception {
            OrderMapper dead = mapperOnDeadDb();
            assertSame(DataResultStatus.SQLDISCON, facade.sel(() -> dead.selectById(1)).status());
        }

        /* exec가 활성화되면 테스트로 해제한다 — 쓰기 실패 분류(SQLDUP/SQLNULL) 검증.
        @Test
        @DisplayName("PK 중복 INSERT는 SQLDUP(rc=20)")
        void duplicateKey() {
            OrderRow dup = new OrderRow();
            dup.setId(1L);
            dup.setCustomerName("dup");
            dup.setAmount(BigDecimal.ONE);
            dup.setStatus("NEW");
            assertSame(DataResultStatus.SQLDUP, facade.exec(() -> orderMapper.insert(dup)).status());
        }

        @Test
        @DisplayName("NOT NULL 위반은 SQLNULL(rc=21)")
        void notNullViolation() {
            OrderRow bad = new OrderRow();
            bad.setId(100L);
            bad.setCustomerName(null);
            bad.setAmount(BigDecimal.ONE);
            bad.setStatus("NEW");
            assertSame(DataResultStatus.SQLNULL, facade.exec(() -> orderMapper.insert(bad)).status());
        }
        */
    }

    @Nested
    @DisplayName("결과 객체 규칙")
    class ResultRules {

        @Test
        @DisplayName("실패 결과의 value() 접근은 그 자리에서 터진다")
        void valueOfFailureThrows() {
            DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(999));
            assertThrows(IllegalStateException.class, r::value);
        }

        @Test
        @DisplayName("성공은 하나 — 데이터 계층 결과도 ok()이고 == SUCCESS")
        void singleSuccess() {
            DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(1));
            assertTrue(r.ok());
            assertSame(CommonResultStatus.SUCCESS, r.status());
        }

        @Test
        @DisplayName("코드표가 실수로 성공 번호(0)를 써도 성공이 아니고, 결과에 담는 순간 거부된다")
        void successCodeTypoRejected() {
            assertFalse(TypoStatus.OOPS.ok());
            assertThrows(IllegalArgumentException.class, () -> ResultWrapper.fail(TypoStatus.OOPS, "번호 실수"));
        }

        @Test
        @DisplayName("데이터 실패를 propagate로 넘겨도 rc 그대로")
        void propagateKeepsStatus() {
            ResultWrapper<String> lifted = facade.sel(() -> orderMapper.selectById(999)).propagate();
            assertSame(DataResultStatus.SQL_NO_DATA, lifted.status());
        }

        @Test
        @DisplayName("성공 결과의 propagate는 거부 — 값이 사라지므로")
        void propagateOfSuccessRejected() {
            DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(1));
            assertThrows(IllegalStateException.class, r::propagate);
        }

        @Test
        @DisplayName("중간모듈 결과(ResultWrapper)도 같은 이름 propagate로 rc 그대로")
        void propagateOfMiddleResult() {
            ResultWrapper<Integer> relayed = service.summarize(999).propagate();
            assertSame(DataResultStatus.SQL_NO_DATA, relayed.status());
        }

        @Test
        @DisplayName("noData()는 0건만 true — 성공·연결 장애는 false, 전파 후에도 유지")
        void noDataOnlyForNoData() throws Exception {
            DataResultWrapper<OrderRow> none = facade.sel(() -> orderMapper.selectById(999));
            assertTrue(none.noData());
            assertTrue(none.fail(), "noData는 fail의 하위 판정");
            assertTrue(none.<String>propagate().noData());

            assertFalse(facade.sel(() -> orderMapper.selectById(1)).noData());

            OrderMapper dead = mapperOnDeadDb();
            DataResultWrapper<OrderRow> discon = facade.sel(() -> dead.selectById(1));
            assertTrue(discon.fail());
            assertFalse(discon.noData(), "연결 장애를 0건으로 읽으면 안 된다");
            assertFalse(ResultWrapper.fail(CommonResultStatus.FAIL, "x").noData());
        }

        @Test
        @DisplayName("cause — DB 예외에서 온 실패는 원래 예외를 들고, 전파·value() 접근 예외까지 이어진다")
        void causeKeptAcrossLayers() throws Exception {
            OrderMapper dead = mapperOnDeadDb();
            DataResultWrapper<OrderRow> r = facade.sel(() -> dead.selectById(1));
            assertTrue(r.cause() != null && DataExceptionRule.isConnectionFailure(r.cause()), String.valueOf(r.cause()));

            ResultWrapper<String> relayed = r.propagate();
            assertSame(r.cause(), relayed.cause());
            assertSame(r.cause(), assertThrows(IllegalStateException.class, relayed::value).getCause());
            assertSame(r.cause(), assertThrows(IllegalStateException.class, r::value).getCause());
        }

        @Test
        @DisplayName("cause — 0건·성공은 예외 없이 판정되므로 null, 성공에 cause를 담는 것은 거부")
        void causeAbsentWithoutException() {
            assertEquals(null, facade.sel(() -> orderMapper.selectById(999)).cause());
            assertEquals(null, facade.sel(() -> orderMapper.selectById(1)).cause());
            assertThrows(IllegalArgumentException.class,
                    () -> new ResultWrapper<>(CommonResultStatus.SUCCESS, "x", null, new RuntimeException()));
        }

        @Test
        @DisplayName("새 실패에 원인 잇기 — ResultWrapper.fail(status, detail, cause)")
        void newFailureWithCause() {
            RuntimeException moduleError = new RuntimeException("모듈 오류 1511");
            ResultWrapper<String> r = ResultWrapper.fail(CommonResultStatus.FAIL, "계좌 조회 실패", moduleError);
            assertSame(moduleError, r.<Integer>propagate().cause());
        }
    }

    @Nested
    @DisplayName("서비스 흐름 (OrderService) — 파사드 rc 분기, 실패 전파, 집계")
    class ServiceFlow {

        @Test
        @DisplayName("C 스타일 분기 — fail() 확인 후 value 사용")
        void cStyleBranch() {
            ResultWrapper<OrderRow> r = service.find(2);
            if (r.fail()) {
                throw new AssertionError("성공이어야 한다: " + r.status().name());
            }
            assertEquals("PAID", r.value().getStatus());
        }

        @Test
        @DisplayName("서비스 요약 성공")
        void summarize() {
            ResultWrapper<String> r = service.summarize(2);
            assertTrue(r.ok());
            assertTrue(r.value().contains("lee"));
        }

        @Test
        @DisplayName("서비스 실패 전파 — rc=10 그대로")
        void summarizeFailurePropagates() {
            assertEquals(10, service.summarize(999).status().code());
        }

        @Test
        @DisplayName("전체 금액 합계 40000.50")
        void totalAmount() {
            ResultWrapper<BigDecimal> r = service.totalAmount();
            assertTrue(r.ok());
            assertEquals(0, new BigDecimal("40000.50").compareTo(r.value()));
        }
    }

    @Nested
    @DisplayName("파사드 밖 매퍼 호출 감시 (MyBatis 인터셉터, 기본 off)")
    class Guard {

        @Test
        @DisplayName("기본은 꺼짐 — 매퍼를 직접 불러도 그대로 실행되고, 감시 장치는 등록조차 되지 않는다")
        void offByDefault() {
            assertEquals("kim", orderMapper.selectById(1).getCustomerName());
            assertTrue(ctx.getBeansOfType(FacadeCallHook.class).isEmpty());
        }

        @Test
        @DisplayName("켜면 파사드 안 호출은 통과, 직접 호출은 SQL 실행 전에 거부")
        void onRejectsDirectCall() {
            try (AnnotationConfigApplicationContext guarded = new AnnotationConfigApplicationContext()) {
                guarded.getEnvironment().getPropertySources().addFirst(new MapPropertySource("guard",
                        Map.of(MyBatisRepoFacadeConfiguration.GUARD_PROPERTY, "true")));
                guarded.register(AppConfig.class);
                guarded.refresh();
                RepoFacade guardedFacade = guarded.getBean(RepoFacade.class);
                OrderMapper guardedMapper = guarded.getBean(OrderMapper.class);

                assertTrue(guardedFacade.sel(() -> guardedMapper.selectById(1)).ok());
                assertTrue(guarded.getBean(OrderService.class).find(1).ok());

                // MyBatis가 감싸서 던지므로 cause 체인에서 감시 예외를 찾는다
                RuntimeException e = assertThrows(RuntimeException.class, () -> guardedMapper.selectById(1));
                assertTrue(DataExceptionRule.hasCause(e, IllegalStateException.class), e.toString());
                assertTrue(e.getMessage().contains("OrderMapper.selectById"), e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("파사드 호출 훅 (FacadeCallHook)")
    class Hooks {

        @Test
        @DisplayName("순서대로 들어가고 역순으로 나온다")
        void entersInOrderLeavesInReverse() {
            List<String> trace = new ArrayList<>();
            try (AnnotationConfigApplicationContext hooked = contextWithHooks(
                    new RecordingHook("first", 1, trace, false), new RecordingHook("second", 2, trace, false))) {
                RepoFacade hookedFacade = hooked.getBean(RepoFacade.class);
                assertTrue(hookedFacade.sel(() -> {
                    trace.add("body");
                    return "row";
                }).ok());
            }
            assertEquals(List.of("first.before", "second.before", "body", "second.after", "first.after"), trace);
        }

        @Test
        @DisplayName("뒤쪽 훅의 before()가 던져도 먼저 들어간 훅의 after()는 불린다 — 본문은 실행되지 않는다")
        void failingBeforeStillUnwindsEnteredHooks() {
            List<String> trace = new ArrayList<>();
            try (AnnotationConfigApplicationContext hooked = contextWithHooks(
                    new RecordingHook("first", 1, trace, false), new RecordingHook("second", 2, trace, true))) {
                RepoFacade hookedFacade = hooked.getBean(RepoFacade.class);
                assertThrows(IllegalStateException.class, () -> hookedFacade.sel(() -> {
                    trace.add("body");
                    return "row";
                }));
            }
            assertEquals(List.of("first.before", "second.before", "first.after"), trace);
        }

        private AnnotationConfigApplicationContext contextWithHooks(RecordingHook... hooks) {
            AnnotationConfigApplicationContext hooked = new AnnotationConfigApplicationContext();
            hooked.register(DefaultRepoFacade.class, ExceptionClassifier.class);
            for (RecordingHook hook : hooks) {
                hooked.registerBean(hook.name, FacadeCallHook.class, () -> hook);
            }
            hooked.refresh();
            return hooked;
        }
    }

    /** 불린 순서를 기록하는 훅. failOnBefore면 before()에서 던진다. */
    private static final class RecordingHook implements FacadeCallHook, Ordered {

        private final String name;
        private final int order;
        private final List<String> trace;
        private final boolean failOnBefore;

        RecordingHook(String name, int order, List<String> trace, boolean failOnBefore) {
            this.name = name;
            this.order = order;
            this.trace = trace;
            this.failOnBefore = failOnBefore;
        }

        @Override
        public void before() {
            trace.add(name + ".before");
            if (failOnBefore) {
                throw new IllegalStateException(name + ".before 실패");
            }
        }

        @Override
        public void after() {
            trace.add(name + ".after");
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    /** 접속 불가 DB에 붙은 매퍼를 만든다 — mybatis-spring의 실제 예외 변환 경로를 그대로 탄다. */
    /** 데이터 접근 기술이 이 예외를 던졌다고 치고 파사드가 매기는 상태. 분류 대상이 아니면(코드 버그) 그대로 던져진다. */
    private ResultStatus statusOfThrown(RuntimeException thrown) {
        return facade.<Object>sel(() -> {
            throw thrown;
        }).status();
    }

    private static OrderMapper mapperOnDeadDb() throws Exception {
        JdbcDataSource deadDb = new JdbcDataSource();
        deadDb.setURL("jdbc:h2:tcp://127.0.0.1:1/nope");   // 아무도 듣지 않는 포트
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(deadDb);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        sqlSessionFactory.getConfiguration().addMapper(OrderMapper.class);
        return new SqlSessionTemplate(sqlSessionFactory).getMapper(OrderMapper.class);
    }

    /** 성공 번호(0)를 실수로 쓴 코드표. */
    private enum TypoStatus implements ResultStatus {
        OOPS(0);

        private final int code;

        TypoStatus(int code) {
            this.code = code;
        }

        @Override
        public int code() {
            return code;
        }
    }
}
