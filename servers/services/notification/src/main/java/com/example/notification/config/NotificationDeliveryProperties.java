package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.delivery")
public class NotificationDeliveryProperties {

    private int maxAttempts = 3;
    private int initialBackoffSeconds = 10;
    private int backoffMultiplier = 2;
    private int retryBatchSize = 100;
    private long retryFixedDelayMs = 5000L;
    private int claimLeaseSeconds = 30;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public void setInitialBackoffSeconds(int initialBackoffSeconds) {
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    public int getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(int backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public int getRetryBatchSize() {
        return retryBatchSize;
    }

    public void setRetryBatchSize(int retryBatchSize) {
        this.retryBatchSize = retryBatchSize;
    }

    public long getRetryFixedDelayMs() {
        return retryFixedDelayMs;
    }

    public void setRetryFixedDelayMs(long retryFixedDelayMs) {
        this.retryFixedDelayMs = retryFixedDelayMs;
    }

    public int getClaimLeaseSeconds() {
        return claimLeaseSeconds;
    }

    public void setClaimLeaseSeconds(int claimLeaseSeconds) {
        this.claimLeaseSeconds = claimLeaseSeconds;
    }
}
