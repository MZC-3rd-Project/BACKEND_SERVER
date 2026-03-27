package com.example.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.ops-alert")
public class NotificationOpsAlertProperties {

    private boolean enabled = true;
    private String toAddress = "ddingsha9@teambind.co.kr";
    private int payloadPreviewLength = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public int getPayloadPreviewLength() {
        return payloadPreviewLength;
    }

    public void setPayloadPreviewLength(int payloadPreviewLength) {
        this.payloadPreviewLength = payloadPreviewLength;
    }
}
