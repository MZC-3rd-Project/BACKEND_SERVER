package com.example.config.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tracing")
public class TracingProperties {

    private boolean enabled = true;
    private float samplingRate = 1.0f;
    private String serviceName;
    private String zipkinEndpoint = "http://localhost:9411/api/v2/spans";
    private String propagationType = "B3";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getSamplingRate() {
        return samplingRate;
    }

    public void setSamplingRate(float samplingRate) {
        this.samplingRate = samplingRate;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getZipkinEndpoint() {
        return zipkinEndpoint;
    }

    public void setZipkinEndpoint(String zipkinEndpoint) {
        this.zipkinEndpoint = zipkinEndpoint;
    }

    public String getPropagationType() {
        return propagationType;
    }

    public void setPropagationType(String propagationType) {
        this.propagationType = propagationType;
    }
}
