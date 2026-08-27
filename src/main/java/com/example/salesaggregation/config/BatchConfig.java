package com.example.salesaggregation.config;

import com.example.salesaggregation.batch.*;
import com.example.salesaggregation.domain.RawSalesRow;
import com.example.salesaggregation.domain.SalesRowValidator;
import com.example.salesaggregation.domain.TaxCalculator;
import com.example.salesaggregation.domain.ValidatedSalesRow;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

@Configuration
public class BatchConfig {
    @Bean("batchTaskExecutor")
    TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("sales-batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("asyncJobLauncher")
    JobLauncher asyncJobLauncher(JobRepository jobRepository,
                                 @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(taskExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }

    @Bean
    @StepScope
    GoogleSalesItemReader googleSalesItemReader(SalesSheetGateway gateway, AppProperties properties) {
        return new GoogleSalesItemReader(gateway, properties);
    }

    @Bean
    @StepScope
    AggregationItemProcessor aggregationItemProcessor(
            @Value("#{jobParameters['executionId']}") String executionId,
            AggregationExecutionRepository executions,
            SalesRowValidator validator,
            TaxCalculator calculator) {
        AggregationExecutionEntity execution = executions.findById(UUID.fromString(executionId)).orElseThrow();
        return new AggregationItemProcessor(validator, calculator, execution.getTaxMode(), execution.getTaxRate());
    }

    @Bean
    @StepScope
    AggregationItemWriter aggregationItemWriter(
            @Value("#{jobParameters['executionId']}") String executionId,
            AggregationWorkStore workStore) {
        return new AggregationItemWriter(UUID.fromString(executionId), workStore);
    }

    @Bean
    Step acquireExecutionLockStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                  AcquireExecutionLockTasklet tasklet) {
        return new StepBuilder("acquireExecutionLockStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    @Bean
    Step prepareExecutionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              PrepareExecutionTasklet tasklet) {
        return new StepBuilder("prepareExecutionStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    Step readAndAggregateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              GoogleSalesItemReader reader, AggregationItemProcessor processor,
                              AggregationItemWriter writer, AppProperties properties) {
        return new StepBuilder("readAndAggregateStep", jobRepository)
                .<RawSalesRow, ValidatedSalesRow>chunk(properties.batch().chunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    Step publishResultStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                           PublishResultTasklet tasklet) {
        return new StepBuilder("publishResultStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    @Bean
    Job salesAggregationJob(JobRepository jobRepository,
                            Step acquireExecutionLockStep,
                            Step prepareExecutionStep,
                            Step readAndAggregateStep,
                            Step publishResultStep,
                            AggregationJobListener listener) {
        return new JobBuilder("salesAggregationJob", jobRepository)
                .listener(listener)
                .start(acquireExecutionLockStep)
                .next(prepareExecutionStep)
                .next(readAndAggregateStep)
                .next(publishResultStep)
                .build();
    }
}
