package com.example.notification.config;

import com.example.notification.service.email.EmailProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.email")
public class NotificationEmailProperties {

    private EmailProviderType provider = EmailProviderType.SMTP;
    private boolean fallbackEnabled = false;
    private EmailProviderType fallbackProvider = EmailProviderType.SMTP;
    private String fromAddress = "no-reply@example.com";
    private String sesRegion = "ap-northeast-2";
    private String configurationSet;

    public EmailProviderType getProvider() {
        return provider;
    }

    public void setProvider(EmailProviderType provider) {
        this.provider = provider;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public EmailProviderType getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(EmailProviderType fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getSesRegion() {
        return sesRegion;
    }

    public void setSesRegion(String sesRegion) {
        this.sesRegion = sesRegion;
    }

    public String getConfigurationSet() {
        return configurationSet;
    }

    public void setConfigurationSet(String configurationSet) {
        this.configurationSet = configurationSet;
    }
}
