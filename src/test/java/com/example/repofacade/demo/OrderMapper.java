package com.example.repofacade.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;

@Mapper
public interface OrderMapper {

    @Select("SELECT id, customer_name, amount, status FROM orders WHERE id = #{id}")
    OrderRow selectById(long id);

    /** 단건 선언이지만 customer_name은 유니크가 아니다 — TOO_MANY 데모용 함정. */
    @Select("SELECT id, customer_name, amount, status FROM orders WHERE customer_name = #{name}")
    OrderRow selectByCustomer(String name);

    /** 같은 SQL을 목록 타입으로 선언 — MyBatis가 selectList로 실행하므로 2건이어도 정상이다. */
    @Select("SELECT id, customer_name, amount, status FROM orders WHERE customer_name = #{name} ORDER BY id")
    List<OrderRow> selectListByCustomer(String name);

    /** Optional 반환 — MyBatis는 단건(selectOne)으로 실행한다. 0건이면 Optional.empty(). */
    @Select("SELECT id, customer_name, amount, status FROM orders WHERE id = #{id}")
    Optional<OrderRow> selectOptionalById(long id);

    /** 배열 반환 — MyBatis는 목록(selectList)으로 실행한다. */
    @Select("SELECT id, customer_name, amount, status FROM orders WHERE status = #{status} ORDER BY id")
    OrderRow[] selectArrayByStatus(String status);

    /** 커서 반환 — 비었는지 미리 알 수 없어 파사드가 거부하는 함정. */
    @Select("SELECT id, customer_name, amount, status FROM orders ORDER BY id")
    Cursor<OrderRow> selectAllAsCursor();

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

    /** 문자 컬럼을 숫자로 받는 함정 — 결과 매핑 오류. 드라이버가 SQLException을 내지만 원인은 매핑(코드)이다. */
    @Select("SELECT customer_name FROM orders WHERE id = #{id}")
    Long selectNameAsNumber(long id);

    /** 파라미터 세팅 단계에서 실패하는 함정 — FailingParamTypeHandler가 값에 따라 드라이버 오류나 연결 끊김을 흉내 낸다. */
    @Select("SELECT id FROM orders WHERE customer_name = "
            + "#{name, typeHandler=com.example.repofacade.demo.FailingParamTypeHandler}")
    Long selectIdWithFailingParam(String name);

    /** SQL 매핑이 없는 메서드 — 매핑 누락 데모용 함정. */
    OrderRow selectWithoutSql(long id);

    @Insert("INSERT INTO orders (id, customer_name, amount, status) "
            + "VALUES (#{id}, #{customerName}, #{amount}, #{status})")
    int insert(OrderRow row);
}
