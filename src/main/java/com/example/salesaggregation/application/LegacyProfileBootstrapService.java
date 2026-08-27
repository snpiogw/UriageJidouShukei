package com.example.salesaggregation.application;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LegacyProfileBootstrapService {
    private final AggregationProfileRepository profiles;
    private final AggregationExecutionRepository executions;
    private final AppProperties properties;

    public LegacyProfileBootstrapService(AggregationProfileRepository profiles,
                                         AggregationExecutionRepository executions,
                                         AppProperties properties) {
        this.profiles = profiles;
        this.executions = executions;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void supplementLegacyProfileAndExecutionsOnce() {
        AggregationProfileEntity legacy = profiles.findForUpdateById(AggregationProfileService.LEGACY_PROFILE_ID)
                .orElseThrow(() -> new IllegalStateException("初期集計設定が見つかりません"));
        String configuredId = properties.sheets().spreadsheetId();
        if (legacy.getSpreadsheetId().isBlank() && configuredId != null && !configuredId.isBlank()) {
            legacy.fillLegacySpreadsheetId(configuredId, "legacy-bootstrap");
            profiles.saveAndFlush(legacy);
        }
        if (legacy.getSpreadsheetId().isBlank()) return;

        List<AggregationExecutionEntity> legacyExecutions = executions.findByProfileId(legacy.getId());
        for (AggregationExecutionEntity execution : legacyExecutions) {
            execution.fillLegacySnapshotIfMissing(legacy.snapshot());
        }
        executions.saveAll(legacyExecutions);
    }
}
