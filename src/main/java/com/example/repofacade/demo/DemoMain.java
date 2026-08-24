package com.example.repofacade.demo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;

import com.example.repofacade.core.data.RepoFacade;
import com.example.repofacade.core.data.DataResultWrapper;
import com.example.repofacade.core.data.DataResultStatus;

/** 파사드 자동 등록이 실제로 동작하는지 시나리오별로 검증한다. 전부 통과하면 종료 코드 0. */
public class DemoMain {

    private static int failures = 0;

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = ctx.getBean(OrderService.class);

            @SuppressWarnings("unchecked")
            RepoFacade<OrderMapper> orders = (RepoFacade<OrderMapper>) ctx.getBeanProvider(
                    ResolvableType.forClassWithGenerics(RepoFacade.class, OrderMapper.class))
                    .getObject();

            // 1. 단건 조회
            DataResultWrapper<OrderRow> found = service.find(1);
            check("단건 조회 성공(rc=0)", found.status().code() == 0);
            check("값 확인(customer=kim)", "kim".equals(found.value().getCustomerName()));
            check("값 확인(amount=10000.00)",
                    new BigDecimal("10000.00").compareTo(found.value().getAmount()) == 0);

            // 2. 없는 id → 예외 없이 SQL_NO_DATA
            DataResultWrapper<OrderRow> missing = service.find(999);
            check("없는 행은 SQL_NO_DATA(rc=10)",
                    missing.fail() && missing.status() == DataResultStatus.SQL_NO_DATA);

            // 3. 실패 결과의 value() 접근은 그 자리에서 터진다
            boolean guarded;
            try {
                missing.value();
                guarded = false;
            } catch (IllegalStateException e) {
                guarded = true;
            }
            check("실패 결과 value() 접근 차단", guarded);

            // 4. selectOne인데 2건 → TooManyResults가 SQLOVF로 분류
            DataResultWrapper<OrderRow> tooMany = orders.one(m -> m.selectByCustomer("lee"));
            check("2건 조회는 SQLOVF(rc=11)", tooMany.status() == DataResultStatus.SQLOVF);

            /* exec가 활성화되면 해제한다 — 쓰기 실패 분류(SQLDUP/SQLNULL) 검증.
            // (exec) PK 중복 INSERT → SQLDUP
            OrderRow dup = new OrderRow();
            dup.setId(1L);
            dup.setCustomerName("dup");
            dup.setAmount(BigDecimal.ONE);
            dup.setStatus("NEW");
            DataResultWrapper<Integer> inserted = orders.exec(m -> m.insert(dup));
            check("PK 중복은 SQLDUP(rc=20)", inserted.status() == DataResultStatus.SQLDUP);

            // (exec) NOT NULL 위반 → SQLNULL
            OrderRow bad = new OrderRow();
            bad.setId(100L);
            bad.setCustomerName(null);
            bad.setAmount(BigDecimal.ONE);
            bad.setStatus("NEW");
            DataResultWrapper<Integer> badInsert = orders.exec(m -> m.insert(bad));
            check("NOT NULL 위반은 SQLNULL(rc=21)",
                    badInsert.status() == DataResultStatus.SQLNULL);
            */

            // 5. 목록 조회
            DataResultWrapper<List<OrderRow>> all = orders.many(m -> m.selectAll());
            check("목록 조회 3건", all.ok() && all.value().size() == 3);

            // 6. C 스타일 사용감
            DataResultWrapper<OrderRow> r = service.find(2);
            if (r.fail()) {
                check("C 스타일 분기(fail())", false);
            } else {
                check("C 스타일 분기(fail() 확인 후 value 사용)",
                        "PAID".equals(r.value().getStatus()));
            }

            // 7. 서비스 흐름 — 파사드 rc 분기, 실패 전파, 집계 (OrderService 참고)
            DataResultWrapper<String> summary = service.summarize(2);
            check("서비스 요약 성공", summary.ok() && summary.value().contains("lee"));

            DataResultWrapper<String> missingSummary = service.summarize(999);
            check("서비스 실패 전파(rc=10 그대로)", missingSummary.status().code() == 10);

            DataResultWrapper<BigDecimal> total = service.totalAmount();
            check("전체 금액 합계 40000.50", total.ok()
                    && new BigDecimal("40000.50").compareTo(total.value()) == 0);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("모든 시나리오 통과");
        } else {
            System.out.println(failures + "개 시나리오 실패");
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name);
        if (!ok) {
            failures++;
        }
    }
}
