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
    private static final long SALES_JOB_LOCK_NAMESPACE = 7_314_991_281L;
    private final DataSource dataSource;
    private final Map<UUID, HeldLock> heldConnections = new ConcurrentHashMap<>();

    public PostgresAdvisoryLockService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean tryLock(UUID executionId, long profileId) {
        long lockKey = lockKeyForProfile(profileId);
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
                statement.setLong(1, lockKey);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        heldConnections.put(executionId, new HeldLock(connection, lockKey));
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
        HeldLock held = heldConnections.remove(executionId);
        if (held == null) return;
        try (Connection connection = held.connection();
             PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, held.lockKey());
            statement.execute();
        } catch (SQLException ignored) {
            // Closing the dedicated connection releases session advisory locks.
        }
    }

    private record HeldLock(Connection connection, long lockKey) {}

    static long lockKeyForProfile(long profileId) {
        return SALES_JOB_LOCK_NAMESPACE + profileId;
    }
}
