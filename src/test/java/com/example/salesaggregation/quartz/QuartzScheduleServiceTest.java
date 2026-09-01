package com.example.salesaggregation.quartz;

import com.example.salesaggregation.application.AggregationProfileService;
import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuartzScheduleServiceTest {
    @Test
    void createsIndependentPersistentJobsAndTriggersForEachProfile() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        AggregationProfileService profiles = mock(AggregationProfileService.class);
        QuartzScheduleService service = new QuartzScheduleService(scheduler, profiles);

        service.apply(profile(10, "店舗A", LocalTime.of(21, 0), true));
        service.apply(profile(20, "店舗B", LocalTime.of(22, 30), true));

        ArgumentCaptor<JobDetail> jobs = ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler, times(2)).addJob(jobs.capture(), eq(false));
        assertThat(jobs.getAllValues()).extracting(job -> job.getKey().getName())
                .containsExactly("aggregation-profile-10", "aggregation-profile-20");
        assertThat(jobs.getAllValues()).extracting(job -> job.getJobDataMap().getLong("profileId"))
                .containsExactly(10L, 20L);

        ArgumentCaptor<Trigger> triggers = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler, times(2)).scheduleJob(triggers.capture());
        assertThat(triggers.getAllValues()).extracting(trigger -> trigger.getKey().getName())
                .containsExactly("aggregation-profile-10-daily", "aggregation-profile-20-daily");
    }

    @Test
    void updatesOnlyTheRequestedProfileTrigger() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);
        QuartzScheduleService service = new QuartzScheduleService(scheduler, mock(AggregationProfileService.class));

        service.apply(profile(10, "店舗A", LocalTime.of(19, 15), true));

        ArgumentCaptor<TriggerKey> key = ArgumentCaptor.forClass(TriggerKey.class);
        verify(scheduler).rescheduleJob(key.capture(), any(Trigger.class));
        assertThat(key.getValue().getName()).isEqualTo("aggregation-profile-10-daily");
        verify(scheduler, never()).deleteJob(any());
    }

    @Test
    void startupReconcilesAllProfilesAndRemovesTheLegacySingleton() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        AggregationProfileService profiles = mock(AggregationProfileService.class);
        when(profiles.list()).thenReturn(List.of(profile(10, "店舗A", LocalTime.NOON, false)));
        QuartzScheduleService service = new QuartzScheduleService(scheduler, profiles);

        service.reconcileAtStartup();

        verify(profiles).list();
        verify(scheduler).addJob(any(JobDetail.class), eq(false));
    }

    private AggregationProfileEntity profile(long id, String name, LocalTime time, boolean enabled) {
        AggregationProfileEntity profile = new AggregationProfileEntity(name, "sheet-" + id, "入力" + id,
                "結果" + id, "エラー" + id, TaxMode.INCLUSIVE, BigDecimal.TEN, enabled, time,
                "Asia/Tokyo", ColumnMapping.DEFAULT, "test");
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }
}
