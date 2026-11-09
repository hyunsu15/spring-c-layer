package com.example.repofacade.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import com.example.repofacade.core.CommonResultStatus;
import com.example.repofacade.core.ResultStatus;
import com.example.repofacade.core.ResultWrapper;
import com.example.repofacade.core.data.DataExceptionRule;
import com.example.repofacade.core.data.DataResultStatus;
import com.example.repofacade.core.data.DataResultWrapper;
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
    @DisplayName("조회 결과 (one / many)")
    class Query {

        @Test
        @DisplayName("단건 조회 성공 — rc=0, 값 그대로")
        void oneFound() {
            DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(1));
            assertEquals(0, r.status().code());
            assertEquals("kim", r.value().getCustomerName());
            assertEquals(0, new BigDecimal("10000.00").compareTo(r.value().getAmount()));
        }

        @Test
        @DisplayName("없는 행은 예외 없이 SQL_NO_DATA(rc=10)")
        void oneMissing() {
            DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(999));
            assertTrue(r.fail());
            assertSame(DataResultStatus.SQL_NO_DATA, r.status());
        }

        @Test
        @DisplayName("selectOne인데 2건 — 감싸진 TooManyResults도 SQLOVF(rc=11)")
        void oneTooMany() {
            assertSame(DataResultStatus.SQLOVF, facade.one(() -> orderMapper.selectByCustomer("lee")).status());
        }

        @Test
        @DisplayName("목록 조회 3건")
        void manyFound() {
            DataResultWrapper<List<OrderRow>> r = facade.many(() -> orderMapper.selectAll());
            assertTrue(r.ok());
            assertEquals(3, r.value().size());
        }

        @Test
        @DisplayName("빈 목록도 SQL_NO_DATA — 단건 조회와 같은 규칙(C의 NOT FOUND)")
        void manyEmpty() {
            assertSame(DataResultStatus.SQL_NO_DATA, facade.many(() -> orderMapper.selectByStatus("CANCELLED")).status());
        }

        @Test
        @DisplayName("감싸지 않은 0건 MAX()는 SQL_NO_DATA — 알려진 한계 (core.data.mybatis 패키지 문서 참고)")
        void aggregateWithoutNvl() {
            assertSame(DataResultStatus.SQL_NO_DATA,
                    facade.one(() -> orderMapper.selectMaxAmountByStatus("CANCELLED")).status());
        }

        @Test
        @DisplayName("NVL(COALESCE)로 감싼 0건 MAX()는 성공(0)")
        void aggregateWithNvl() {
            DataResultWrapper<BigDecimal> r = facade.one(() -> orderMapper.selectMaxAmountByStatusOrZero("CANCELLED"));
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
                    () -> facade.one(() -> orderMapper.selectFromTypoTable(1)));
        }

        @Test
        @DisplayName("SQL 매핑 누락은 rc로 바꾸지 않고 그대로 던진다")
        void missingMappingThrows() {
            assertThrows(BindingException.class, () -> facade.one(() -> orderMapper.selectWithoutSql(1)));
        }

        @Test
        @DisplayName("DB 접속 불가는 SQLDISCON(rc=30) — MyBatis가 MyBatisSystemException으로 감싸 와도")
        void deadDbIsDiscon() throws Exception {
            OrderMapper dead = mapperOnDeadDb();
            assertSame(DataResultStatus.SQLDISCON, facade.one(() -> dead.selectById(1)).status());
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
            DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(999));
            assertThrows(IllegalStateException.class, r::value);
        }

        @Test
        @DisplayName("성공은 하나 — 데이터 계층 결과도 ok()이고 == SUCCESS")
        void singleSuccess() {
            DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(1));
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
            ResultWrapper<String> lifted = facade.one(() -> orderMapper.selectById(999)).propagate();
            assertSame(DataResultStatus.SQL_NO_DATA, lifted.status());
        }

        @Test
        @DisplayName("성공 결과의 propagate는 거부 — 값이 사라지므로")
        void propagateOfSuccessRejected() {
            DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(1));
            assertThrows(IllegalStateException.class, r::propagate);
        }

        @Test
        @DisplayName("중간모듈 결과(ResultWrapper)도 같은 이름 propagate로 rc 그대로")
        void propagateOfMiddleResult() {
            ResultWrapper<Integer> relayed = service.summarize(999).propagate();
            assertSame(DataResultStatus.SQL_NO_DATA, relayed.status());
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
        @DisplayName("기본은 꺼짐 — 매퍼를 직접 불러도 그대로 실행된다")
        void offByDefault() {
            assertEquals("kim", orderMapper.selectById(1).getCustomerName());
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

                assertTrue(guardedFacade.one(() -> guardedMapper.selectById(1)).ok());
                assertTrue(guarded.getBean(OrderService.class).find(1).ok());

                // MyBatis가 감싸서 던지므로 cause 체인에서 감시 예외를 찾는다
                RuntimeException e = assertThrows(RuntimeException.class, () -> guardedMapper.selectById(1));
                assertTrue(DataExceptionRule.hasCause(e, IllegalStateException.class), e.toString());
                assertTrue(e.getMessage().contains("OrderMapper.selectById"), e.getMessage());
            }
        }
    }

    /** 접속 불가 DB에 붙은 매퍼를 만든다 — mybatis-spring의 실제 예외 변환 경로를 그대로 탄다. */
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
