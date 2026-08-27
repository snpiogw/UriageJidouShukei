package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.RowError;
import com.example.salesaggregation.application.AdminViewModels.ExecutionView;
import com.example.salesaggregation.application.AdminViewModels.SettingsView;
import com.example.salesaggregation.infrastructure.persistence.*;
import com.example.salesaggregation.quartz.QuartzScheduleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminQueryService {
    private final SettingsService settingsService;
    private final AggregationExecutionRepository executions;
    private final AggregationWorkStore workStore;
    private final QuartzScheduleService schedules;

    public AdminQueryService(SettingsService settingsService, AggregationExecutionRepository executions,
                             AggregationWorkStore workStore, QuartzScheduleService schedules) {
        this.settingsService = settingsService;
        this.executions = executions;
        this.workStore = workStore;
        this.schedules = schedules;
    }

    @Transactional(readOnly = true)
    public DashboardData dashboard() {
        List<AggregationExecutionEntity> history = executions.findAllByOrderByRequestedAtDesc(PageRequest.of(0, 10));
        AggregationExecutionEntity latestAttempt = history.isEmpty() ? null : history.getFirst();
        AggregationExecutionEntity latestSuccess = executions.findFirstByStatusInOrderByCompletedAtDesc(
                List.of(ExecutionStatus.SUCCESS, ExecutionStatus.SUCCESS_WITH_WARNINGS)).orElse(null);
        return new DashboardData(SettingsView.from(settingsService.current()), history.stream().map(ExecutionView::from).toList(),
                latestAttempt == null ? null : ExecutionView.from(latestAttempt),
                latestSuccess == null ? null : ExecutionView.from(latestSuccess),
                schedules.nextExecution());
    }

    @Transactional(readOnly = true)
    public ExecutionDetail execution(UUID id) {
        AggregationExecutionEntity execution = executions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("実行履歴が見つかりません"));
        List<RowError> errors = workStore.errors(id);
        return new ExecutionDetail(ExecutionView.from(execution), errors);
    }

    public record DashboardData(
            SettingsView settings,
            List<ExecutionView> history,
            ExecutionView latestAttempt,
            ExecutionView latestSuccess,
            ZonedDateTime nextExecution) {}

    public record ExecutionDetail(ExecutionView execution, List<RowError> errors) {}
}
