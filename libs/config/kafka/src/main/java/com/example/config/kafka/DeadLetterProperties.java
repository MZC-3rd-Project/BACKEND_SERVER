package com.example.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.dlq")
public class DeadLetterProperties {

    private boolean retryEnabled = true;
    private int consumerFailureAlertThreshold = 3;
    private int retryFailureAlertThreshold = 3;
    private int retryBatchSize = 20;
    private long retryFixedDelayMs = 30000L;
    private int retryInitialDelaySeconds = 60;
    private int retryBackoffMultiplier = 2;
    private int retryMaxDelaySeconds = 1800;
    private int republishTimeoutSeconds = 10;

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public int getConsumerFailureAlertThreshold() {
        return consumerFailureAlertThreshold;
    }

    public void setConsumerFailureAlertThreshold(int consumerFailureAlertThreshold) {
        this.consumerFailureAlertThreshold = consumerFailureAlertThreshold;
    }

    public int getRetryFailureAlertThreshold() {
        return retryFailureAlertThreshold;
    }

    public void setRetryFailureAlertThreshold(int retryFailureAlertThreshold) {
        this.retryFailureAlertThreshold = retryFailureAlertThreshold;
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

    public int getRetryInitialDelaySeconds() {
        return retryInitialDelaySeconds;
    }

    public void setRetryInitialDelaySeconds(int retryInitialDelaySeconds) {
        this.retryInitialDelaySeconds = retryInitialDelaySeconds;
    }

    public int getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(int retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public int getRetryMaxDelaySeconds() {
        return retryMaxDelaySeconds;
    }

    public void setRetryMaxDelaySeconds(int retryMaxDelaySeconds) {
        this.retryMaxDelaySeconds = retryMaxDelaySeconds;
    }

    public int getRepublishTimeoutSeconds() {
        return republishTimeoutSeconds;
    }

    public void setRepublishTimeoutSeconds(int republishTimeoutSeconds) {
        this.republishTimeoutSeconds = republishTimeoutSeconds;
    }
}
