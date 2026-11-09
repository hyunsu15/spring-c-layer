package com.example.repofacade.demo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.repofacade.core.ResultWrapper;
import com.example.repofacade.core.data.DataResultWrapper;
import com.example.repofacade.core.data.RepoFacade;

/**
 * 파사드를 쓰는 서비스 예시.
 *
 * - 주입: 사내 관례대로 @Autowired 필드. 매퍼는 평소처럼 받고, 파사드(공용 빈 1개)만 하나 더 받는다.
 * - 흐름: 파사드로 매퍼 호출 → rc 분기 → 실패는 rc 그대로 상위 전파, 성공만 가공 (C 스타일).
 * - 경계: 파사드에서 받은 DataResultWrapper를 ResultWrapper로 바꿔 내보낸다 (DataResultWrapper는 서비스가 만들 수 없다).
 */
@Service
public class OrderService {

    @Autowired
    private RepoFacade facade;

    @Autowired
    private OrderMapper orderMapper;

    /** 단건 조회 — 파사드 결과를 그대로 중간모듈 결과로 옮기는 가장 단순한 형태. */
    public ResultWrapper<OrderRow> find(long id) {
        DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(id));
        if (r.fail()) {
            return r.propagate();
        }
        return ResultWrapper.ok(r.value());
    }

    /** 조회 후 가공 — 실패(rc != 0)면 그 rc 그대로 상위에 전파하고, 성공만 요약 문자열로 만든다. */
    public ResultWrapper<String> summarize(long id) {
        DataResultWrapper<OrderRow> r = facade.one(() -> orderMapper.selectById(id));
        if (r.fail()) {
            return r.propagate();
        }
        OrderRow row = r.value();
        return ResultWrapper.ok(row.getCustomerName() + " 주문 " + row.getAmount() + "원 (" + row.getStatus() + ")");
    }

    /** 목록 조회 후 집계 — many() 결과를 C 스타일 루프로 합산한다. */
    public ResultWrapper<BigDecimal> totalAmount() {
        DataResultWrapper<List<OrderRow>> r = facade.many(() -> orderMapper.selectAll());
        if (r.fail()) {
            return r.propagate();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderRow row : r.value()) {
            total = total.add(row.getAmount());
        }
        return ResultWrapper.ok(total);
    }
}
