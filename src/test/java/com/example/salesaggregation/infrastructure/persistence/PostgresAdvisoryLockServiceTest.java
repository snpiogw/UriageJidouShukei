package com.example.salesaggregation.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresAdvisoryLockServiceTest {
    @Test
    void usesTheSameLockForTheSameProfileAndDifferentLocksForDifferentProfiles() {
        assertThat(PostgresAdvisoryLockService.lockKeyForProfile(10))
                .isEqualTo(PostgresAdvisoryLockService.lockKeyForProfile(10));
        assertThat(PostgresAdvisoryLockService.lockKeyForProfile(10))
                .isNotEqualTo(PostgresAdvisoryLockService.lockKeyForProfile(11));
    }
}
