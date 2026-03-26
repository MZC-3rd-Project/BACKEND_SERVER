package com.example.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties(MetricsProperties.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "app.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MetricsAutoConfiguration {

    @Bean
    public org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer<MeterRegistry> donmoaMeterRegistryCustomizer(
            MetricsProperties properties,
            Environment environment
    ) {
        return registry -> registry.config().commonTags(
                "service_name", resolveServiceName(properties, environment),
                "env", resolveEnvironment(properties, environment),
                "cluster", resolveCluster(properties)
        );
    }

    @Bean
    public MeterFilter donmoaHttpRequestMetricsFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(io.micrometer.core.instrument.Meter.Id id,
                                                         DistributionStatisticConfig config) {
                String name = id.getName();
                if (!"http.server.requests".equals(name) && !"spring.cloud.gateway.requests".equals(name)) {
                    return config;
                }

                return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .serviceLevelObjectives(
                                0.05,
                                0.1,
                                0.25,
                                0.5,
                                1.0,
                                2.0,
                                5.0
                        )
                        .build()
                        .merge(config);
            }
        };
    }

    private String resolveServiceName(MetricsProperties properties, Environment environment) {
        if (StringUtils.hasText(properties.getServiceName())) {
            return properties.getServiceName().trim();
        }
        String appName = environment.getProperty("spring.application.name");
        if (StringUtils.hasText(appName)) {
            return appName.trim();
        }
        return "application";
    }

    private String resolveEnvironment(MetricsProperties properties, Environment environment) {
        if (StringUtils.hasText(properties.getEnvironment())) {
            return properties.getEnvironment().trim();
        }

        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles != null && activeProfiles.length > 0 && StringUtils.hasText(activeProfiles[0])) {
            return activeProfiles[0].trim();
        }
        return "default";
    }

    private String resolveCluster(MetricsProperties properties) {
        if (StringUtils.hasText(properties.getCluster())) {
            return properties.getCluster().trim();
        }
        return "cluster";
    }
}
