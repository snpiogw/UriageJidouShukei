package com.example.salesaggregation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class V4MigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migratesExistingSettingsAndFailedExecutionsWithoutDeletingData() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("3").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                update aggregation_settings
                   set tax_mode='EXCLUSIVE', tax_rate=8.0000, execution_time='22:30:00', version=7
                 where id=1
                """);
        UUID failedId = UUID.randomUUID();
        jdbc.update("""
                insert into aggregation_execution
                    (id,trigger_type,status,tax_mode,tax_rate,settings_version,requested_at,
                     source_count,valid_count,invalid_count,error_code,summary)
                values (?,'MANUAL','FAILED','EXCLUSIVE',8.0000,7,current_timestamp,10,8,2,'BATCH_FAILED','failed')
                """, failedId);

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> profile = jdbc.queryForMap("""
                select profile_name,tax_mode,tax_rate,execution_time,time_zone,date_column,staff_column,
                       product_column,quantity_column,unit_price_column,version
                  from aggregation_profile where id=1
                """);
        assertThat(profile.get("profile_name")).isEqualTo("既存設定");
        assertThat(profile.get("tax_mode")).isEqualTo("EXCLUSIVE");
        assertThat(profile.get("version")).isEqualTo(7L);
        assertThat(profile.get("date_column")).isEqualTo("日付");
        assertThat(jdbc.queryForObject(
                "select profile_id from aggregation_execution where id=?", Long.class, failedId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select profile_name_snapshot from aggregation_execution where id=?", String.class, failedId))
                .isEqualTo("既存設定");
        assertThat(jdbc.queryForObject(
                "select count(*) from aggregation_execution where id=?", Integer.class, failedId)).isEqualTo(1);
    }
}
