package com.example.salesaggregation.application;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LegacyProfileBootstrapServiceTest {
    @Test
    void supplementsTheLegacyProfileAndMissingExecutionSnapshotOnlyOnce() {
        AggregationProfileEntity profile = profile("");
        AggregationExecutionEntity execution = new AggregationExecutionEntity(
                UUID.randomUUID(), TriggerType.MANUAL, TaxMode.INCLUSIVE, BigDecimal.TEN, 0);
        AggregationProfileRepository profiles = mock(AggregationProfileRepository.class);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        when(profiles.findForUpdateById(1L)).thenReturn(Optional.of(profile));
        when(executions.findByProfileId(1L)).thenReturn(List.of(execution));

        LegacyProfileBootstrapService service = new LegacyProfileBootstrapService(
                profiles, executions, properties("legacy-from-env"));
        service.supplementLegacyProfileAndExecutionsOnce();

        assertThat(profile.getSpreadsheetId()).isEqualTo("legacy-from-env");
        assertThat(execution.profileSnapshot().spreadsheetId()).isEqualTo("legacy-from-env");

        profile.update("既存設定", "changed-in-db", "売上データ", "集計結果", "エラーログ",
                TaxMode.INCLUSIVE, BigDecimal.TEN, true, LocalTime.of(21, 0), "Asia/Tokyo",
                ColumnMapping.DEFAULT, "admin");
        service.supplementLegacyProfileAndExecutionsOnce();

        assertThat(execution.profileSnapshot().spreadsheetId()).isEqualTo("legacy-from-env");
    }

    @Test
    void neverOverwritesAnExistingDatabaseSpreadsheetIdFromTheEnvironment() {
        AggregationProfileEntity profile = profile("database-value");
        AggregationProfileRepository profiles = mock(AggregationProfileRepository.class);
        AggregationExecutionRepository executions = mock(AggregationExecutionRepository.class);
        when(profiles.findForUpdateById(1L)).thenReturn(Optional.of(profile));
        when(executions.findByProfileId(1L)).thenReturn(List.of());

        new LegacyProfileBootstrapService(profiles, executions, properties("environment-value"))
                .supplementLegacyProfileAndExecutionsOnce();

        assertThat(profile.getSpreadsheetId()).isEqualTo("database-value");
        verify(profiles, never()).saveAndFlush(profile);
    }

    private AggregationProfileEntity profile(String spreadsheetId) {
        AggregationProfileEntity profile = new AggregationProfileEntity("既存設定", spreadsheetId,
                "売上データ", "集計結果", "エラーログ", TaxMode.INCLUSIVE, BigDecimal.TEN,
                true, LocalTime.of(21, 0), "Asia/Tokyo", ColumnMapping.DEFAULT, "system");
        ReflectionTestUtils.setField(profile, "id", 1L);
        return profile;
    }

    private AppProperties properties(String spreadsheetId) {
        return new AppProperties(new AppProperties.Sheets(spreadsheetId, "売上データ", "集計結果", "エラーログ", 10000, 500),
                new AppProperties.Security("admin", "hash"), new AppProperties.Batch(500, 3));
    }
}
