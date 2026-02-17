package com.example.event.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private boolean enabled = true;
    private boolean immediatePublishEnabled = true;
    private final Relay relay = new Relay();
    private final Cleanup cleanup = new Cleanup();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isImmediatePublishEnabled() {
        return immediatePublishEnabled;
    }

    public void setImmediatePublishEnabled(boolean immediatePublishEnabled) {
        this.immediatePublishEnabled = immediatePublishEnabled;
    }

    public Relay getRelay() {
        return relay;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public static class Relay {
        private boolean enabled = true;
        private long fixedDelayMs = 5000;
        private int batchSize = 100;
        private int maxInFlight = 32;
        private int maxRetries = 5;
        private int maxErrorMessageLength = 240;
        private long sendingStaleThresholdSeconds = 120;
        private long fetchBeforeSeconds = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getMaxInFlight() {
            return maxInFlight;
        }

        public void setMaxInFlight(int maxInFlight) {
            this.maxInFlight = maxInFlight;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxErrorMessageLength() {
            return maxErrorMessageLength;
        }

        public void setMaxErrorMessageLength(int maxErrorMessageLength) {
            this.maxErrorMessageLength = maxErrorMessageLength;
        }

        public long getSendingStaleThresholdSeconds() {
            return sendingStaleThresholdSeconds;
        }

        public void setSendingStaleThresholdSeconds(long sendingStaleThresholdSeconds) {
            this.sendingStaleThresholdSeconds = sendingStaleThresholdSeconds;
        }

        public long getFetchBeforeSeconds() {
            return fetchBeforeSeconds;
        }

        public void setFetchBeforeSeconds(long fetchBeforeSeconds) {
            this.fetchBeforeSeconds = fetchBeforeSeconds;
        }
    }

    public static class Cleanup {
        private boolean enabled = true;
        private String cron = "0 0 3 * * *";
        private int retentionDays = 7;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
