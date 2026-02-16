package com.example.security.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.context")
public class SecurityContextProperties {

    private boolean cleanupFilterEnabled = true;
    private String signingKey;
    private long maxAgeMillis = 300_000;

    public boolean isCleanupFilterEnabled() {
        return cleanupFilterEnabled;
    }

    public void setCleanupFilterEnabled(boolean cleanupFilterEnabled) {
        this.cleanupFilterEnabled = cleanupFilterEnabled;
    }

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public long getMaxAgeMillis() {
        return maxAgeMillis;
    }

    public void setMaxAgeMillis(long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }
}
