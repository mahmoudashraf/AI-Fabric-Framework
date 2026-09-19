package com.ai.fabric.runtime.smartbrain;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

@Component
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainScheduleRegistrar implements ApplicationRunner {

    static final String GROUP = "loomai-smart-brain";

    private final Scheduler scheduler;
    private final SmartBrainConfigurationService configurationService;

    public SmartBrainScheduleRegistrar(
        Scheduler scheduler,
        SmartBrainConfigurationService configurationService
    ) {
        this.scheduler = scheduler;
        this.configurationService = configurationService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Set<JobKey> desiredJobs = new HashSet<>();
        for (SmartBrainRuntimeConfig.Schedule schedule : configurationService.current().schedules()) {
            if (!schedule.enabled()) {
                continue;
            }
            configurationService.requireTrigger(schedule.triggerCode());
            JobKey jobKey = JobKey.jobKey(schedule.code(), GROUP);
            TriggerKey triggerKey = TriggerKey.triggerKey(schedule.code(), GROUP);
            desiredJobs.add(jobKey);

            JobDetail job = JobBuilder.newJob(SmartBrainScheduledTriggerJob.class)
                .withIdentity(jobKey)
                .withDescription("Immutable LoomAI Smart Brain schedule " + schedule.code())
                .usingJobData("scheduleCode", schedule.code())
                .usingJobData("triggerCode", schedule.triggerCode())
                .storeDurably(true)
                .requestRecovery(true)
                .build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(
                    CronScheduleBuilder.cronSchedule(schedule.cron())
                        .inTimeZone(TimeZone.getTimeZone(schedule.zoneId()))
                        .withMisfireHandlingInstructionDoNothing()
                )
                .build();

            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        }

        for (JobKey existing : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
            if (!desiredJobs.contains(existing)) {
                scheduler.deleteJob(existing);
            }
        }
    }
}
