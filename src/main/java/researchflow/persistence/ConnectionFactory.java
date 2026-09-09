package researchflow.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class ConnectionFactory {
    private final String jdbcUrl;
    private final java.util.concurrent.locks.ReentrantReadWriteLock gate = new java.util.concurrent.locks.ReentrantReadWriteLock(true);

    public <T> T inOperation(java.util.function.Supplier<T> action) {
        gate.readLock().lock();
        try {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            return action.get();
        } finally { gate.readLock().unlock(); }
    }

    public <T> T exclusive(java.util.concurrent.Callable<T> action) throws Exception {
        gate.writeLock().lockInterruptibly();
        try { return action.call(); } finally { gate.writeLock().unlock(); }
    }


    public ConnectionFactory(Path databasePath) {
        try {
            var parent = databasePath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new PersistenceException("Could not create the database directory.", exception);
        }
    }

    public ConnectionFactory(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("A SQLite JDBC URL is required.");
        }
        this.jdbcUrl = jdbcUrl;
    }

    public Connection open() {
        gate.readLock().lock();
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
                // WAL + NORMAL trade a small, well-understood durability window (the last few OS-cached
                // writes on a hard power loss) for a large reduction in per-transaction fsync cost — the
                // standard recommendation for write-heavy SQLite workloads such as a bulk dataset import
                // or a large version snapshot, and it also shortens how long a writer holds the lock,
                // reducing SQLITE_BUSY collisions with concurrent reads/writes.
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            var delegate = connection;
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) {
                            if (closed.compareAndSet(false, true)) {
                                try { delegate.close(); } finally { gate.readLock().unlock(); }
                            }
                            return null;
                        }
                        try { return method.invoke(delegate, args); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                    });
        } catch (SQLException | RuntimeException exception) {
            if (connection != null) try { connection.close(); } catch (SQLException close) { exception.addSuppressed(close); }
            gate.readLock().unlock();
            throw new PersistenceException("Could not open the local database.", exception);
        }
    }
}
