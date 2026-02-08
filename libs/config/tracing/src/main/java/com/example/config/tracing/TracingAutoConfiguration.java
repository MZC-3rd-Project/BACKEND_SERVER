package com.example.config.tracing;

import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TracingProperties.class)
@ConditionalOnProperty(
        prefix = "app.tracing",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TracingAutoConfiguration {

    private final TracingProperties properties;

    public TracingAutoConfiguration(TracingProperties properties) {
        this.properties = properties;
        logTracingConfiguration();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public MdcTracingFilter mdcTracingFilter(Tracer tracer) {
        log.info("Registering MdcTracingFilter for trace context propagation to MDC");
        return new MdcTracingFilter(tracer);
    }

    private void logTracingConfiguration() {
        log.info("Distributed Tracing Auto-Configuration initialized");
        log.info("  - Enabled: {}", properties.isEnabled());
        log.info("  - Sampling Rate: {}%", properties.getSamplingRate() * 100);
        log.info("  - Service Name: {}",
                properties.getServiceName() != null ? properties.getServiceName() : "from spring.application.name");
        log.info("  - Zipkin Endpoint: {}", properties.getZipkinEndpoint());
        log.info("  - Propagation Type: {}", properties.getPropagationType());
    }
}
