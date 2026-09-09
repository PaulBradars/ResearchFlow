package researchflow.persistence;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionManager {
    private final ConnectionFactory connections;

    public TransactionManager(ConnectionFactory connections) {
        this.connections = connections;
    }

    public <T> T inTransaction(TransactionWork<T> work) {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                var result = work.execute(connection);
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Cancelled before commit.");
                connection.commit();
                return result;
            } catch (Exception exception) {
                rollback(connection, exception);
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new PersistenceException("Database transaction failed.", exception);
            } finally {
                resetAutoCommit(connection);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Database transaction could not be completed.", exception);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void resetAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // The connection is closed immediately; the original failure remains authoritative.
        }
    }

    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }
}
