package com.example.security.signature;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.context")
public class SecuritySignatureProperties {

    private String signingKey;
    private long maxAgeMillis = 300_000;
    private boolean parserEnabled = true;

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

    public boolean isParserEnabled() {
        return parserEnabled;
    }

    public void setParserEnabled(boolean parserEnabled) {
        this.parserEnabled = parserEnabled;
    }
}
