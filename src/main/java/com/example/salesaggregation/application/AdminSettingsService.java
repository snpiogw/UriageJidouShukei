package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.infrastructure.persistence.AggregationSettingsEntity;
import com.example.salesaggregation.quartz.QuartzScheduleService;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;

@Service
public class AdminSettingsService {
    private final SettingsService settingsService;
    private final QuartzScheduleService scheduleService;

    public AdminSettingsService(SettingsService settingsService, QuartzScheduleService scheduleService) {
        this.settingsService = settingsService;
        this.scheduleService = scheduleService;
    }

    public AggregationSettingsEntity update(TaxMode mode, BigDecimal taxRate, boolean enabled, LocalTime time,
                                            long expectedVersion, String actor) throws SchedulerException {
        AggregationSettingsEntity saved = settingsService.update(mode, taxRate, enabled, time, expectedVersion, actor);
        scheduleService.apply(saved);
        return saved;
    }
}
