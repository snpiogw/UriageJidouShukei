package com.example.salesaggregation.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfileConfigurationLockService {
    private static final long CONFIGURATION_LOCK_SEED = 9_142_771_009L;
    private final JdbcTemplate jdbc;

    public ProfileConfigurationLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The lock is released automatically with the surrounding database transaction. */
    public void lockSpreadsheet(String spreadsheetId) {
        jdbc.queryForObject(
                "select pg_advisory_xact_lock(hashtextextended(?, ?))",
                (rs, rowNum) -> Boolean.TRUE,
                "profile-config:" + spreadsheetId.trim(), CONFIGURATION_LOCK_SEED);
    }
}
