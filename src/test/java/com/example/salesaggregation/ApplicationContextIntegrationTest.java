package com.example.salesaggregation;

import com.example.salesaggregation.application.AggregationProfileService;
import com.example.salesaggregation.infrastructure.persistence.PostgresAdvisoryLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "app.security.admin-password-hash=$2a$12$VYtZGVD7GT0UqsoHY2gwmua/yabgWi1b1vC2A6lQjzIYqOQyTqNWu"
})
@AutoConfigureMockMvc
class ApplicationContextIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    AggregationProfileService profileService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostgresAdvisoryLockService executionLocks;

    @Test
    void appliesAllMigrationsAndKeepsDailyTimeAsJapanLocalTime() {
        Integer migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);

        assertThat(migrations).isEqualTo(4);
        assertThat(profileService.get(1).getExecutionTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(profileService.get(1).getTimeZone()).isEqualTo("Asia/Tokyo");
        assertThat(profileService.get(1).getProfileName()).isEqualTo("既存設定");
        assertThat(profileService.get(1).columnMapping().headers())
                .containsExactly("日付", "担当者", "商品名", "数量", "単価");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='public' and table_name='aggregation_profile'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema='public' and table_name='aggregation_execution' and column_name='profile_id'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rendersProfilePagesAndKeepsCsrfProtection() throws Exception {
        mockMvc.perform(get("/admin/profiles"))
                .andExpect(status().isOk()).andExpect(view().name("admin"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("既存設定")));
        mockMvc.perform(get("/admin/profiles/new"))
                .andExpect(status().isOk()).andExpect(view().name("profile-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("列マッピング")));
        mockMvc.perform(get("/admin/profiles/1/executions"))
                .andExpect(status().isOk()).andExpect(view().name("profile-history"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("既存設定 の実行履歴")));
        mockMvc.perform(post("/admin/executions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preventsOnlyTheSameProfileFromRunningConcurrently() {
        UUID first = UUID.randomUUID();
        UUID sameProfile = UUID.randomUUID();
        UUID otherProfile = UUID.randomUUID();
        try {
            assertThat(executionLocks.tryLock(first, 1L)).isTrue();
            assertThat(executionLocks.tryLock(sameProfile, 1L)).isFalse();
            assertThat(executionLocks.tryLock(otherProfile, 2L)).isTrue();
        } finally {
            executionLocks.unlock(first);
            executionLocks.unlock(sameProfile);
            executionLocks.unlock(otherProfile);
        }
    }
}
