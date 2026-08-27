package com.example.salesaggregation.application;

import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import com.example.salesaggregation.quartz.QuartzScheduleService;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

@Service
public class AdminProfileService {
    private final AggregationProfileService profiles;
    private final QuartzScheduleService schedules;

    public AdminProfileService(AggregationProfileService profiles, QuartzScheduleService schedules) {
        this.profiles = profiles;
        this.schedules = schedules;
    }

    public AggregationProfileEntity create(ProfileCommand command, String actor) throws SchedulerException {
        AggregationProfileEntity saved = profiles.create(command, actor);
        schedules.apply(saved);
        return saved;
    }

    public AggregationProfileEntity update(long id, long version, ProfileCommand command, String actor)
            throws SchedulerException {
        AggregationProfileEntity saved = profiles.update(id, version, command, actor);
        schedules.apply(saved);
        return saved;
    }
}
