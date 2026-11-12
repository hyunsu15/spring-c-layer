package com.example.repofacade.core.data;

import static com.example.repofacade.core.data.DataExceptionRule.hasCause;
import static com.example.repofacade.core.data.DataExceptionRule.isConnectionFailure;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Component;

/**
 * 데이터 접근 예외 → DataResultStatus 분류.
 *
 * 원칙: C가 rc로 받던 것 — 데이터 상태(0건, 중복 등)와 DB 장애(연결 단절 등) — 만 DataResultWrapper로 바꾸고,
 * 코드가 원인인 실패(잘못된 SQL, 매핑 누락, 파라미터·결과 매핑 오류, NPE 등)는 그대로 던진다.
 * 이런 실패는 레거시에 없던, 전환하며 생긴 버그다. rc로 바꾸면 "조회오류도 정상 진행" 같은 레거시 분기에 묻힌다.
 *
 * 여기에는 스프링 DataAccessException 계층(기술 무관) 규칙만 둔다.
 * 기술별 규칙은 {@link DataExceptionRule} 빈으로 붙는다 (각 연동 패키지에서 등록).
 */
@Component
public class ExceptionClassifier {

    private static final Logger log = LoggerFactory.getLogger(ExceptionClassifier.class);

    private final List<DataExceptionRule> rules;

    public ExceptionClassifier(ObjectProvider<DataExceptionRule> rules) {
        this.rules = rules.orderedStream().toList();
    }

    /** 분류 가능하면 실패 DataResultWrapper를 반환하고, 아니면(버그 포함) 원래 예외를 다시 던진다. */
    public <T> DataResultWrapper<T> toResult(RuntimeException e) {
        DataResultStatus status = statusOf(e);
        if (status == null) {
            throw e;
        }
        if (status == DataResultStatus.SYSTEM_ERROR) {
            log.error("[repo-facade] 미분류 데이터 접근 오류", e);
        } else {
            log.warn("[repo-facade] {}: {}", status, rootMessage(e));
        }
        return DataResultWrapper.fail(status, rootMessage(e));
    }

    private DataResultStatus statusOf(RuntimeException e) {
        // 버그 판정이 먼저 — 아래 규칙들(특히 마지막 SYSTEM_ERROR)이 버그를 rc로 바꾸기 전에 걸러낸다
        if (isCodeBug(e)) {
            return null;
        }
        // 기술별 규칙 먼저 — 래핑된 예외를 아래 일반 규칙이 SYSTEM_ERROR로 뭉개기 전에 판별
        for (DataExceptionRule rule : rules) {
            DataResultStatus status = rule.classify(e);
            if (status != null) {
                return status;
            }
        }
        // EmptyResult는 IncorrectResultSize의 하위 타입이라 먼저 봐야 한다 (아니면 0건이 SQLOVF가 된다)
        if (e instanceof EmptyResultDataAccessException) {
            return DataResultStatus.SQL_NO_DATA;
        }
        if (e instanceof IncorrectResultSizeDataAccessException) {
            return DataResultStatus.SQLOVF;
        }
        if (e instanceof DuplicateKeyException) {
            return DataResultStatus.SQLDUP;
        }
        if (e instanceof DataIntegrityViolationException) {
            return DataResultStatus.SQLNULL;
        }
        if (isConnectionFailure(e)) {
            return DataResultStatus.SQLDISCON;
        }
        // 그 외 DB가 돌려준 오류 (스프링 변환을 거치지 않고 SQLException을 품은 예외 포함)
        if (e instanceof DataAccessException || hasCause(e, SQLException.class)) {
            return DataResultStatus.SYSTEM_ERROR;
        }
        return null;
    }

    /**
     * 고칠 대상(코드 버그)인가. 기술별 규칙의 판정 + 스프링 공통 규칙:
     * 잘못된 SQL(BadSqlGrammarException, 하위 타입 TypeMismatch 등 포함)과 API 오용.
     */
    private boolean isCodeBug(RuntimeException e) {
        for (DataExceptionRule rule : rules) {
            if (rule.isCodeBug(e)) {
                return true;
            }
        }
        return e instanceof InvalidDataAccessResourceUsageException
                || e instanceof InvalidDataAccessApiUsageException;
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
