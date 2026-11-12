/**
 * MyBatis 연동. MyBatis와 mybatis-spring이 클래스패스에 있을 때만 활성화된다 ({@link MyBatisRepoFacadeConfiguration}).
 * MyBatis에 관한 코드와 주의사항은 이 패키지에만 둔다 — core.data는 MyBatis를 모른다.
 *
 * <h2>MyBatis로 쓸 때 주의</h2>
 *
 * <p><b>단일 컬럼·집계 조회는 SQL에서 NVL(COALESCE)로 감쌀 것.</b>
 * MyBatis는 "0건"과 "NULL 한 행"(0건 MAX/SUM, 값이 NULL인 단일 컬럼)을 둘 다 null로 돌려주므로,
 * 감싸지 않으면 {@code RepoFacade.sel}이 행이 있어도 SQL_NO_DATA를 준다 (Oracle·C에서는 NOT FOUND가 아닌 경우).
 * 레거시 C도 indicator 없이 NULL을 fetch하면 -1405라서 같은 이유로 NVL을 썼다.
 * 여러 컬럼을 담는 Result 객체는 모든 컬럼이 NULL일 때만 null이라 해당 없다.
 *
 * <p><b>단건·목록은 매퍼 메서드의 반환 타입으로 갈린다.</b> MyBatis는 단건 타입(Row 객체, 스칼라, Optional,
 * {@code @MapKey} 없는 Map)이면 selectOne으로 실행해 2건 이상에서 TooManyResults를 던지고(→ SQLOVF),
 * List·배열·{@code @MapKey} Map이면 selectList/selectMap으로 실행해 몇 건이든 정상이다.
 * C의 SELECT INTO 자리(SQLOVF로 분기하던 SQLW)는 단건 타입으로 선언할 것.
 * {@code Cursor} 반환은 비었는지 미리 알 수 없어 {@code RepoFacade.sel}이 거부한다.
 *
 * <p><b>MyBatis는 예외를 MyBatisSystemException 등으로 감싸서 던진다.</b>
 * TooManyResults와 연결 획득 실패(CannotGetJdbcConnectionException)도 감싸여 오므로 타입이 아니라 cause 체인으로 판별한다
 * ({@link MyBatisExceptionRule}, 그리고 core.data.ExceptionClassifier의 연결 실패 판정).
 */
package com.example.repofacade.core.data.mybatis;
