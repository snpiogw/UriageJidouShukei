package com.example.salesaggregation.application;

import com.example.salesaggregation.config.AppProperties;
import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
public class ExecutionRetentionService {
    private static final Logger log = LoggerFactory.getLogger(ExecutionRetentionService.class);
    private static final List<ExecutionStatus> TERMINAL_STATUSES = Arrays.stream(ExecutionStatus.values())
            .filter(ExecutionStatus::isTerminal).toList();

    private final AggregationExecutionRepository executions;
    private final AppProperties properties;

    public ExecutionRetentionService(AggregationExecutionRepository executions, AppProperties properties) {
        this.executions = executions;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.retention.cleanup-cron}", zone = "Asia/Tokyo")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(properties.retention().executionHistoryDays(), ChronoUnit.DAYS);
        long deleted = executions.deleteByStatusInAndCompletedAtBefore(TERMINAL_STATUSES, cutoff);
        if (deleted > 0) log.info("Deleted {} expired aggregation executions", deleted);
    }
}
