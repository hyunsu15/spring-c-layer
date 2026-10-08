package com.example.repofacade.core.data;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Component;

/**
 * 데이터 접근 예외 → DataResultStatus 분류.
 *
 * 원칙: 데이터가 원인인 실패만 DataResultWrapper로 바꾸고,
 * 코드가 원인인 실패(NPE, 매핑 누락 등 비-데이터접근 예외)는 그대로 던진다
 * — 복구 대상이 아니라 고칠 버그이기 때문.
 *
 * 여기에는 스프링 DataAccessException 계층(기술 무관) 규칙만 둔다.
 * MyBatis 등 기술별 규칙은 {@link DataExceptionRule} 빈으로 붙는다 (core.data.mybatis 참고).
 */
@Component
public class ExceptionClassifier {

    private static final Logger log = LoggerFactory.getLogger(ExceptionClassifier.class);

    private final List<DataExceptionRule> rules;

    public ExceptionClassifier(ObjectProvider<DataExceptionRule> rules) {
        this.rules = rules.orderedStream().toList();
    }

    /** 분류 가능하면 실패 DataResultWrapper를 반환하고, 아니면 원래 예외를 다시 던진다. */
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
        // 연결 획득 실패(CannotGetJdbcConnection 등) 또는 작업 중 연결 끊김
        if (e instanceof DataAccessResourceFailureException
                || e instanceof RecoverableDataAccessException) {
            return DataResultStatus.SQLDISCON;
        }
        if (e instanceof DataAccessException) {
            return DataResultStatus.SYSTEM_ERROR;
        }
        return null;
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
