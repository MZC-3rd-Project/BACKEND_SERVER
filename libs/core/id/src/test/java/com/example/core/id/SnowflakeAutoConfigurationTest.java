package com.example.core.id;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    SnowflakeAutoConfiguration.class
            ));

    @Test
    void createsSnowflakeWithDefaultProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Snowflake.class);
            assertThat(context).hasSingleBean(SnowflakeProperties.class);
            assertThat(context.getBean(Snowflake.class).nextId()).isPositive();
        });
    }

    @Test
    void appliesCustomSnowflakeProperties() {
        contextRunner
                .withPropertyValues(
                        "app.snowflake.datacenter-id=3",
                        "app.snowflake.worker-id=7"
                )
                .run(context -> {
                    SnowflakeProperties properties = context.getBean(SnowflakeProperties.class);
                    assertThat(properties.getDatacenterId()).isEqualTo(3);
                    assertThat(properties.getWorkerId()).isEqualTo(7);
                });
    }
}
