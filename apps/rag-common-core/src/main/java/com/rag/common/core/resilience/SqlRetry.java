package com.rag.common.core.resilience;

import java.sql.SQLException;

/**
 * Classifies JDBC failures that are safe to retry for idempotent statements.
 * Only connection loss ({@code 08}), transaction rollback / deadlock /
 * serialization failure ({@code 40}), insufficient resources ({@code 53}) and
 * {@code 57P03} are transient. Integrity violations ({@code 23}) and missing
 * tables ({@code 42P01}) must never be retried.
 */
public final class SqlRetry {

    private static final String CONNECTION_EXCEPTION = "08";
    private static final String TRANSACTION_ROLLBACK = "40";
    private static final String INSUFFICIENT_RESOURCES = "53";
    private static final String CANNOT_CONNECT_NOW = "57P03";

    private SqlRetry() {
    }

    public static boolean isTransient(String sqlState) {
        if (sqlState == null || sqlState.length() < CONNECTION_EXCEPTION.length()) {
            return false;
        }
        return sqlState.startsWith(CONNECTION_EXCEPTION)
                || sqlState.startsWith(TRANSACTION_ROLLBACK)
                || sqlState.startsWith(INSUFFICIENT_RESOURCES)
                || sqlState.equals(CANNOT_CONNECT_NOW);
    }

    public static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && isTransient(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** Wraps a checked {@link SQLException} so it can flow through a {@link RetryExecutor}. */
    public static final class Failure extends RuntimeException {

        private final SQLException sqlException;

        public Failure(SQLException cause) {
            super(cause.getMessage(), cause);
            this.sqlException = cause;
        }

        public SQLException sqlException() {
            return sqlException;
        }
    }
}
