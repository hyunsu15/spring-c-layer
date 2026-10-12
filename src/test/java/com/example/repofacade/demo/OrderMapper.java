package com.example.repofacade.demo;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper {

    @Select("SELECT id, customer_name, amount, status FROM orders WHERE id = #{id}")
    OrderRow selectById(long id);

    /** 단건 선언이지만 customer_name은 유니크가 아니다 — TOO_MANY 데모용 함정. */
    @Select("SELECT id, customer_name, amount, status FROM orders WHERE customer_name = #{name}")
    OrderRow selectByCustomer(String name);

    @Select("SELECT id, customer_name, amount, status FROM orders ORDER BY id")
    List<OrderRow> selectAll();

    @Select("SELECT id, customer_name, amount, status FROM orders WHERE status = #{status} ORDER BY id")
    List<OrderRow> selectByStatus(String status);

    /** 0건 집계를 감싸지 않은 함정 — 결과는 'NULL 한 행'인데 MyBatis가 null을 줘서 파사드는 SQL_NO_DATA로 본다. */
    @Select("SELECT MAX(amount) FROM orders WHERE status = #{status}")
    BigDecimal selectMaxAmountByStatus(String status);

    /** 규칙대로 감싼 집계 — 0건이어도 0 한 행이라 성공 (Oracle에선 NVL). */
    @Select("SELECT COALESCE(MAX(amount), 0) FROM orders WHERE status = #{status}")
    BigDecimal selectMaxAmountByStatusOrZero(String status);

    /** 테이블명 오타 — 코드 버그 데모용 함정. */
    @Select("SELECT id FROM order_typo WHERE id = #{id}")
    Long selectFromTypoTable(long id);

    /** SQL 매핑이 없는 메서드 — 매핑 누락 데모용 함정. */
    OrderRow selectWithoutSql(long id);

    @Insert("INSERT INTO orders (id, customer_name, amount, status) "
            + "VALUES (#{id}, #{customerName}, #{amount}, #{status})")
    int insert(OrderRow row);
}
