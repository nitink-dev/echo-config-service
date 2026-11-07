package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.SlideScanProgressConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Configures a dynamic scheduler for slide scan timeout monitoring.
 */
@Configuration
@EnableScheduling
public class SlideScanTimeoutSchedulerConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SlideScanTimeoutSchedulerConfig.class);

    private final SlideScanTimeoutMonitorService monitorService;
    private final SlideScanProgressConfig config;

    public SlideScanTimeoutSchedulerConfig(SlideScanTimeoutMonitorService monitorService, SlideScanProgressConfig config) {
        this.monitorService = monitorService;
        this.config = config;
    }

    /**
     * Registers the timeout monitoring task and dynamically calculates the next execution time based on the current configuration.
     * Configuration updates received through Spring Cloud Bus are automatically picked up when calculating future executions.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                monitorService::monitorTimeoutScans,
                triggerContext -> {
                    try {
                        SlideScanProgressConfig.Timeout timeout = config.getTimeout();
                        if (timeout == null) {
                            log.warn("Timeout configuration is null. Using default 5 minute interval");
                            return Instant.now().plus(5, ChronoUnit.MINUTES);
                        }
                        if (timeout.getCheck() == null) {
                            log.warn("Timeout check configuration is null. Using default 5 minute interval");
                            return Instant.now().plus(5, ChronoUnit.MINUTES);
                        }

                        if (triggerContext.lastCompletion() == null) {
                            long initialDelay = timeout.getCheck().getInitialDelay();
                            log.info("Scheduling first execution after {} minute(s)", initialDelay);
                            log.info("Starting slide scan timeout monitoring job with Configuration [enabled={}, timeout={}m, interval={}m, initialDelay={}m, lockTtl={}m, parallelism={}]",
                                    timeout.isEnabled(), timeout.getMinutes(), timeout.getCheck().getInterval(), timeout.getCheck().getInitialDelay(), timeout.getLock().getTtlMinutes(), timeout.getProcessing().getParallelism());
                            return Instant.now().plus(initialDelay, ChronoUnit.MINUTES);
                        }
                        /* For subsequent executions, use the current interval from the refreshed configuration.*/
                        long interval = timeout.getCheck().getInterval();
                        return triggerContext.lastCompletion().plus(interval, ChronoUnit.MINUTES);

                    } catch (Exception e) {
                        log.error("Failed while calculating next scheduler execution.", e);
                        return Instant.now().plus(5, ChronoUnit.MINUTES);
                    }
                });
    }
}