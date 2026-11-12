package com.example.repofacade.demo;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 파라미터 세팅 단계의 실패를 흉내 내는 데모용 핸들러 (H2로는 재현하기 어려운 상황).
 * <ul>
 *   <li>보통 값 — 드라이버가 타입을 못 받아들이는 경우. Oracle에서 null 파라미터에 jdbcType을 빠뜨렸을 때의
 *       "부적합한 열 유형: 1111"과 같은 모양이다. 매핑(코드)이 원인이다.</li>
 *   <li>{@link #DISCONNECTED} — 세팅 도중 연결이 끊긴 경우. 같은 자리에서 났지만 코드 버그가 아니다.</li>
 * </ul>
 */
public class FailingParamTypeHandler extends BaseTypeHandler<String> {

    public static final String DISCONNECTED = "disconnected";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (DISCONNECTED.equals(parameter)) {
            throw new SQLRecoverableException("Closed Connection");
        }
        throw new SQLException("Invalid column type: 1111", "99999", 17004);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
