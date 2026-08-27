package com.example.salesaggregation.quartz;

import com.example.salesaggregation.application.SettingsService;
import com.example.salesaggregation.infrastructure.persistence.AggregationSettingsEntity;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

@Service
public class QuartzScheduleService {
    private static final JobKey JOB_KEY = new JobKey("sales-aggregation", "sales");
    private static final TriggerKey TRIGGER_KEY = new TriggerKey("daily-sales-aggregation", "sales");
    private final Scheduler scheduler;
    private final SettingsService settingsService;

    public QuartzScheduleService(Scheduler scheduler, SettingsService settingsService) {
        this.scheduler = scheduler;
        this.settingsService = settingsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAtStartup() throws SchedulerException {
        apply(settingsService.current());
    }

    public synchronized void apply(AggregationSettingsEntity settings) throws SchedulerException {
        if (!scheduler.checkExists(JOB_KEY)) {
            scheduler.addJob(JobBuilder.newJob(SalesAggregationQuartzJob.class)
                    .withIdentity(JOB_KEY)
                    .storeDurably()
                    .requestRecovery()
                    .build(), false);
        }
        if (!settings.isAutoEnabled()) {
            if (scheduler.checkExists(TRIGGER_KEY)) scheduler.unscheduleJob(TRIGGER_KEY);
            return;
        }
        String cron = String.format("0 %d %d * * ?", settings.getExecutionTime().getMinute(),
                settings.getExecutionTime().getHour());
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(cron)
                .inTimeZone(TimeZone.getTimeZone(settings.getTimeZone()))
                .withMisfireHandlingInstructionFireAndProceed();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .withSchedule(schedule)
                .build();
        if (scheduler.checkExists(TRIGGER_KEY)) scheduler.rescheduleJob(TRIGGER_KEY, trigger);
        else scheduler.scheduleJob(trigger);
    }

    public ZonedDateTime nextExecution() {
        try {
            Trigger trigger = scheduler.getTrigger(TRIGGER_KEY);
            Date next = trigger == null ? null : trigger.getNextFireTime();
            return next == null ? null : next.toInstant().atZone(java.time.ZoneId.of("Asia/Tokyo"));
        } catch (SchedulerException ex) {
            return null;
        }
    }
}
