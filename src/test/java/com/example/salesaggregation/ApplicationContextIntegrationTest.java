package com.example.salesaggregation;

import com.example.salesaggregation.application.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.security.admin-password-hash=$2a$12$VYtZGVD7GT0UqsoHY2gwmua/yabgWi1b1vC2A6lQjzIYqOQyTqNWu"
})
class ApplicationContextIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    SettingsService settingsService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliesAllMigrationsAndKeepsDailyTimeAsJapanLocalTime() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);

        assertThat(migrations).isEqualTo(3);
        assertThat(settingsService.current().getExecutionTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(settingsService.current().getTimeZone()).isEqualTo("Asia/Tokyo");
    }
}
