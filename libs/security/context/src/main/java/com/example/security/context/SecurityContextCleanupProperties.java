package com.example.security.context;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.context")
public class SecurityContextCleanupProperties {

    private boolean cleanupFilterEnabled = true;

    public boolean isCleanupFilterEnabled() {
        return cleanupFilterEnabled;
    }

    public void setCleanupFilterEnabled(boolean cleanupFilterEnabled) {
        this.cleanupFilterEnabled = cleanupFilterEnabled;
    }
}
