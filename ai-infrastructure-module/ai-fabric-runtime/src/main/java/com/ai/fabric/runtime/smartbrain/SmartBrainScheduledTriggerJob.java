package com.ai.fabric.runtime.smartbrain;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@DisallowConcurrentExecution
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainScheduledTriggerJob extends QuartzJobBean {

    private final SmartBrainOperationService operationService;

    public SmartBrainScheduledTriggerJob(SmartBrainOperationService operationService) {
        this.operationService = operationService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String scheduleCode = context.getMergedJobDataMap().getString("scheduleCode");
        String triggerCode = context.getMergedJobDataMap().getString("triggerCode");
        Instant scheduledTime = context.getScheduledFireTime() == null
            ? Instant.now()
            : context.getScheduledFireTime().toInstant();
        operationService.submitScheduled(scheduleCode, triggerCode, scheduledTime);
    }
}
