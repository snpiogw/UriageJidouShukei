package com.example.salesaggregation.quartz;

import com.example.salesaggregation.application.AggregationProfileService;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

@Service
public class QuartzScheduleService {
    private static final String GROUP = "sales-profiles";
    private static final JobKey LEGACY_JOB_KEY = new JobKey("sales-aggregation", "sales");
    private static final TriggerKey LEGACY_TRIGGER_KEY = new TriggerKey("daily-sales-aggregation", "sales");
    private final Scheduler scheduler;
    private final AggregationProfileService profiles;

    public QuartzScheduleService(Scheduler scheduler, AggregationProfileService profiles) {
        this.scheduler = scheduler;
        this.profiles = profiles;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public void reconcileAtStartup() throws SchedulerException {
        removeLegacySchedule();
        for (AggregationProfileEntity profile : profiles.list()) apply(profile);
    }

    public synchronized void apply(AggregationProfileEntity profile) throws SchedulerException {
        JobKey jobKey = jobKey(profile.getId());
        TriggerKey triggerKey = triggerKey(profile.getId());
        if (!scheduler.checkExists(jobKey)) {
            scheduler.addJob(JobBuilder.newJob(SalesAggregationQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("profileId", profile.getId())
                    .storeDurably()
                    .requestRecovery()
                    .build(), false);
        }
        if (!profile.isActive() || !profile.isAutoEnabled() || profile.getSpreadsheetId().isBlank()) {
            if (scheduler.checkExists(triggerKey)) scheduler.unscheduleJob(triggerKey);
            return;
        }
        String cron = String.format("0 %d %d * * ?", profile.getExecutionTime().getMinute(),
                profile.getExecutionTime().getHour());
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(cron)
                .inTimeZone(TimeZone.getTimeZone(profile.getTimeZone()))
                .withMisfireHandlingInstructionFireAndProceed();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(schedule)
                .build();
        if (scheduler.checkExists(triggerKey)) scheduler.rescheduleJob(triggerKey, trigger);
        else scheduler.scheduleJob(trigger);
    }

    public ZonedDateTime nextExecution(AggregationProfileEntity profile) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(profile.getId()));
            Date next = trigger == null ? null : trigger.getNextFireTime();
            return next == null ? null : next.toInstant().atZone(ZoneId.of(profile.getTimeZone()));
        } catch (SchedulerException ex) {
            return null;
        }
    }

    JobKey jobKey(long profileId) {
        return new JobKey("aggregation-profile-" + profileId, GROUP);
    }

    TriggerKey triggerKey(long profileId) {
        return new TriggerKey("aggregation-profile-" + profileId + "-daily", GROUP);
    }

    private void removeLegacySchedule() throws SchedulerException {
        if (scheduler.checkExists(LEGACY_TRIGGER_KEY)) scheduler.unscheduleJob(LEGACY_TRIGGER_KEY);
        if (scheduler.checkExists(LEGACY_JOB_KEY)) scheduler.deleteJob(LEGACY_JOB_KEY);
    }
}
