package com.example.salesaggregation.application;

import com.example.salesaggregation.application.AdminViewModels.ExecutionView;
import com.example.salesaggregation.application.AdminViewModels.ProfileView;
import com.example.salesaggregation.domain.RowError;
import com.example.salesaggregation.infrastructure.persistence.*;
import com.example.salesaggregation.quartz.QuartzScheduleService;
import org.springframework.data.domain.PageRequest;
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

    public AdminQueryService(AggregationProfileService profiles, AggregationExecutionRepository executions,
                             AggregationWorkStore workStore, QuartzScheduleService schedules) {
        this.profiles = profiles;
        this.executions = executions;
        this.workStore = workStore;
        this.schedules = schedules;
    }

    @Transactional(readOnly = true)
    public List<ProfileView> profiles() {
        return profiles.list().stream().map(p -> ProfileView.from(p, schedules.nextExecution(p))).toList();
    }

    @Transactional(readOnly = true)
    public ProfileHistory profileHistory(long profileId) {
        AggregationProfileEntity profile = profiles.get(profileId);
        List<ExecutionView> history = executions.findByProfileIdOrderByRequestedAtDesc(profileId, PageRequest.of(0, 100))
                .stream().map(ExecutionView::from).toList();
        return new ProfileHistory(ProfileView.from(profile, schedules.nextExecution(profile)), history);
    }

    @Transactional(readOnly = true)
    public ExecutionDetail execution(UUID id) {
        AggregationExecutionEntity execution = executions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("実行履歴が見つかりません"));
        return new ExecutionDetail(ExecutionView.from(execution), workStore.errors(id));
    }

    public record ProfileHistory(ProfileView profile, List<ExecutionView> history) {}
    public record ExecutionDetail(ExecutionView execution, List<RowError> errors) {}
}
