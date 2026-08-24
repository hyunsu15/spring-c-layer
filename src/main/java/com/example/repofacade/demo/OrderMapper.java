package com.example.repofacade.demo;

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

    @Insert("INSERT INTO orders (id, customer_name, amount, status) "
            + "VALUES (#{id}, #{customerName}, #{amount}, #{status})")
    int insert(OrderRow row);
}
