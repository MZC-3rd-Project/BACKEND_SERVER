package com.example.event.inbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inbox")
public class InboxProperties {

    private boolean enabled = true;
    private boolean immediateTriggerEnabled = true;
    private final Worker worker = new Worker();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isImmediateTriggerEnabled() {
        return immediateTriggerEnabled;
    }

    public void setImmediateTriggerEnabled(boolean immediateTriggerEnabled) {
        this.immediateTriggerEnabled = immediateTriggerEnabled;
    }

    public Worker getWorker() {
        return worker;
    }

    public static class Worker {
        private boolean enabled = true;
        private long fixedDelayMs = 2000;
        private int batchSize = 100;
        private long leaseSeconds = 30;
        private int maxRetryCount = 5;
        private long baseRetryDelaySeconds = 2;
        private long maxRetryDelaySeconds = 300;
        private int maxErrorMessageLength = 240;
        private int staleRecoveryBatchSize = 100;
        private int maxDrainLoops = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getLeaseSeconds() {
            return leaseSeconds;
        }

        public void setLeaseSeconds(long leaseSeconds) {
            this.leaseSeconds = leaseSeconds;
        }

        public int getMaxRetryCount() {
            return maxRetryCount;
        }

        public void setMaxRetryCount(int maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }

        public long getBaseRetryDelaySeconds() {
            return baseRetryDelaySeconds;
        }

        public void setBaseRetryDelaySeconds(long baseRetryDelaySeconds) {
            this.baseRetryDelaySeconds = baseRetryDelaySeconds;
        }

        public long getMaxRetryDelaySeconds() {
            return maxRetryDelaySeconds;
        }

        public void setMaxRetryDelaySeconds(long maxRetryDelaySeconds) {
            this.maxRetryDelaySeconds = maxRetryDelaySeconds;
        }

        public int getMaxErrorMessageLength() {
            return maxErrorMessageLength;
        }

        public void setMaxErrorMessageLength(int maxErrorMessageLength) {
            this.maxErrorMessageLength = maxErrorMessageLength;
        }

        public int getStaleRecoveryBatchSize() {
            return staleRecoveryBatchSize;
        }

        public void setStaleRecoveryBatchSize(int staleRecoveryBatchSize) {
            this.staleRecoveryBatchSize = staleRecoveryBatchSize;
        }

        public int getMaxDrainLoops() {
            return maxDrainLoops;
        }

        public void setMaxDrainLoops(int maxDrainLoops) {
            this.maxDrainLoops = maxDrainLoops;
        }
    }
}
