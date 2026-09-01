package com.example.salesaggregation.application;

import com.example.salesaggregation.application.AdminViewModels.ExecutionView;
import com.example.salesaggregation.application.AdminViewModels.ProfileView;
import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.RowError;
import com.example.salesaggregation.infrastructure.persistence.*;
import com.example.salesaggregation.quartz.QuartzScheduleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminQueryService {
    private final AggregationProfileService profiles;
    private final AggregationExecutionRepository executions;
    private final AggregationWorkStore workStore;
    private final QuartzScheduleService schedules;
    private final ExecutionAttemptService attempts;

    public AdminQueryService(AggregationProfileService profiles, AggregationExecutionRepository executions,
                             AggregationWorkStore workStore, QuartzScheduleService schedules,
                             ExecutionAttemptService attempts) {
        this.profiles = profiles;
        this.executions = executions;
        this.workStore = workStore;
        this.schedules = schedules;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public List<ProfileView> profiles() {
        return profiles.list().stream().map(p -> ProfileView.from(p, schedules.nextExecution(p),
                executions.findFirstByProfileIdOrderByRequestedAtDesc(p.getId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public ProfileHistory profileHistory(long profileId) {
        return profileHistory(profileId, 0, null);
    }

    @Transactional(readOnly = true)
    public ProfileHistory profileHistory(long profileId, int page, ExecutionStatus status) {
        AggregationProfileEntity profile = profiles.get(profileId);
        Page<AggregationExecutionEntity> entities = status == null
                ? executions.findByProfileIdOrderByRequestedAtDesc(profileId, PageRequest.of(Math.max(page, 0), 25))
                : executions.findByProfileIdAndStatusOrderByRequestedAtDesc(profileId, status,
                        PageRequest.of(Math.max(page, 0), 25));
        Page<ExecutionView> history = entities.map(ExecutionView::from);
        return new ProfileHistory(ProfileView.from(profile, schedules.nextExecution(profile),
                executions.findFirstByProfileIdOrderByRequestedAtDesc(profileId).orElse(null)), history, status);
    }

    @Transactional(readOnly = true)
    public ExecutionDetail execution(UUID id) {
        AggregationExecutionEntity execution = executions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("実行履歴が見つかりません"));
        ExecutionView view = ExecutionView.from(execution);
        return new ExecutionDetail(view, workStore.errors(id), attempts.list(id).stream()
                .map(item -> AdminViewModels.AttemptView.from(item, view.timeZone())).toList());
    }

    public record ProfileHistory(ProfileView profile, Page<ExecutionView> history, ExecutionStatus statusFilter) {}
    public record ExecutionDetail(ExecutionView execution, List<RowError> errors,
                                  List<AdminViewModels.AttemptView> attempts) {}
}
