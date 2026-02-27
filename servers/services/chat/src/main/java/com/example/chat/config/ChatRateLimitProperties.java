package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.rate-limit")
public class ChatRateLimitProperties {

    private int perSecond = 8;
    private int perMinute = 100;
    private int keyTtlSeconds = 120;

    public int getPerSecond() {
        return perSecond;
    }

    public void setPerSecond(int perSecond) {
        this.perSecond = perSecond;
    }

    public int getPerMinute() {
        return perMinute;
    }

    public void setPerMinute(int perMinute) {
        this.perMinute = perMinute;
    }

    public int getKeyTtlSeconds() {
        return keyTtlSeconds;
    }

    public void setKeyTtlSeconds(int keyTtlSeconds) {
        this.keyTtlSeconds = keyTtlSeconds;
    }
}
