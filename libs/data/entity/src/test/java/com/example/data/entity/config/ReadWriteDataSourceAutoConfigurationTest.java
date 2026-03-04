package com.example.data.entity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.data.entity.datasource.TransactionRoutingDataSource;
import com.example.data.entity.datasource.UseWriteDataSourceAspect;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReadWriteDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ReadWriteDataSourceAutoConfiguration.class
            ));

    @Test
    void doesNotCreateRoutingDataSourceWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("writeDataSource");
            assertThat(context).doesNotHaveBean("readDataSource");
            assertThat(context).doesNotHaveBean(TransactionRoutingDataSource.class);
        });
    }

    @Test
    void enablesRoutingWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.read-write-routing.enabled=true",
                        "spring.datasource.url=jdbc:h2:mem:write-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password="
                )
                .run(context -> {
                    assertThat(context).hasBean("writeDataSource");
                    assertThat(context).hasBean("readDataSource");
                    assertThat(context.getBean(DataSource.class)).isInstanceOf(TransactionRoutingDataSource.class);
                });
    }

    @Test
    void explicitFalseDisablesRouting() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.read-write-routing.enabled=false",
                        "spring.datasource.url=jdbc:h2:mem:write-profile-false;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password="
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean("writeDataSource");
                    assertThat(context).doesNotHaveBean("readDataSource");
                });
    }

    @Test
    void usesWriteDataSourceAsReadFallbackWhenReadConfigMissing() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.read-write-routing.enabled=true",
                        "spring.datasource.url=jdbc:h2:mem:write;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password="
                )
                .run(context -> {
                    DataSource writeDataSource = context.getBean("writeDataSource", DataSource.class);
                    DataSource readDataSource = context.getBean("readDataSource", DataSource.class);
                    DataSource routingDataSource = context.getBean(DataSource.class);

                    assertThat(readDataSource).isSameAs(writeDataSource);
                    assertThat(routingDataSource).isInstanceOf(TransactionRoutingDataSource.class);
                    assertThat(context).hasSingleBean(UseWriteDataSourceAspect.class);
                });
    }

    @Test
    void createsSeparateReadDataSourceWhenReadConfigProvided() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.read-write-routing.enabled=true",
                        "spring.datasource.url=jdbc:h2:mem:write2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "app.datasource.read.url=jdbc:h2:mem:read2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "app.datasource.read.username=sa",
                        "app.datasource.read.password="
                )
                .run(context -> {
                    DataSource writeDataSource = context.getBean("writeDataSource", DataSource.class);
                    DataSource readDataSource = context.getBean("readDataSource", DataSource.class);

                    assertThat(readDataSource).isNotSameAs(writeDataSource);
                });
    }
}
