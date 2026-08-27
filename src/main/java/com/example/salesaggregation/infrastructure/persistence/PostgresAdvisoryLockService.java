package com.example.salesaggregation.infrastructure.persistence;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PostgresAdvisoryLockService {
    private static final long SALES_JOB_LOCK = 7_314_991_281L;
    private final DataSource dataSource;
    private final Map<UUID, Connection> heldConnections = new ConcurrentHashMap<>();

    public PostgresAdvisoryLockService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean tryLock(UUID executionId) {
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
                statement.setLong(1, SALES_JOB_LOCK);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        heldConnections.put(executionId, connection);
                        return true;
                    }
                }
            }
            connection.close();
            return false;
        } catch (SQLException ex) {
            throw new IllegalStateException("集計実行ロックを取得できません", ex);
        }
    }

    public void unlock(UUID executionId) {
        Connection connection = heldConnections.remove(executionId);
        if (connection == null) return;
        try (connection;
             PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, SALES_JOB_LOCK);
            statement.execute();
        } catch (SQLException ignored) {
            // Closing the dedicated connection releases session advisory locks.
        }
    }
}
