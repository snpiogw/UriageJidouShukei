package com.example.salesaggregation.quartz;

import com.example.salesaggregation.application.AggregationLaunchService;
import com.example.salesaggregation.domain.TriggerType;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class SalesAggregationQuartzJob implements Job {
    private final AggregationLaunchService launchService;

    public SalesAggregationQuartzJob(AggregationLaunchService launchService) {
        this.launchService = launchService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        launchService.launch(TriggerType.SCHEDULED);
    }
}
