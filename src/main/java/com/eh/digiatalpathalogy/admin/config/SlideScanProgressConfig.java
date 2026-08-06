package com.eh.digiatalpathalogy.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


@Configuration
@ConfigurationProperties(prefix = "slide-scan-process")
public class SlideScanProgressConfig {

    private Map<String, Map<String, Double>> services;
    private Timeout timeout;

    public Map<String, Map<String, Double>> getServices() {
        return services;
    }

    public void setServices(Map<String, Map<String, Double>> services) {
        this.services = services;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public static class Timeout {

        private boolean enabled;
        private long minutes;
        private Check check;
        private Lock lock;
        private Processing processing;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMinutes() {
            return minutes;
        }

        public void setMinutes(long minutes) {
            this.minutes = minutes;
        }

        public Check getCheck() {
            return check;
        }

        public void setCheck(Check check) {
            this.check = check;
        }

        public Lock getLock() {
            return lock;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }

        public Processing getProcessing() {
            return processing;
        }

        public void setProcessing(Processing processing) {
            this.processing = processing;
        }
    }

    public static class Check {
        private long interval;
        private long initialDelay;

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public long getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
        }
    }

    public static class Lock {
        private long ttlMinutes;

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class Processing {
        private int parallelism;

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }
    }
}
