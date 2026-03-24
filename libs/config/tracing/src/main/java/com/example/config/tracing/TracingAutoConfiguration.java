package com.example.config.tracing;

import brave.sampler.Sampler;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    @ConditionalOnClass(Sampler.class)
    @ConditionalOnMissingBean(Sampler.class)
    public Sampler braveSampler() {
        float probability = Math.max(0.0f, Math.min(1.0f, properties.getSamplingRate()));
        return Sampler.create(probability);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class ServletTracingConfiguration {

        @Bean
        public MdcTracingFilter mdcTracingFilter(Tracer tracer) {
            log.info("Registering MdcTracingFilter for trace context propagation to MDC");
            return new MdcTracingFilter(tracer);
        }
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
