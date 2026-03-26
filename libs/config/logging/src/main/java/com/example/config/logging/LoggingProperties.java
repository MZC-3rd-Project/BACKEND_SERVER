package com.example.config.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

    private boolean requestIdEnabled = true;
    private String requestIdHeader = "X-Request-Id";
    private boolean writeRequestIdResponseHeader = true;

    public boolean isRequestIdEnabled() {
        return requestIdEnabled;
    }

    public void setRequestIdEnabled(boolean requestIdEnabled) {
        this.requestIdEnabled = requestIdEnabled;
    }

    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    public void setRequestIdHeader(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    public boolean isWriteRequestIdResponseHeader() {
        return writeRequestIdResponseHeader;
    }

    public void setWriteRequestIdResponseHeader(boolean writeRequestIdResponseHeader) {
        this.writeRequestIdResponseHeader = writeRequestIdResponseHeader;
    }
}
