package com.example.data.entity.config;

import com.example.data.entity.datasource.DataSourceRoute;
import com.example.data.entity.datasource.TransactionRoutingDataSource;
import com.example.data.entity.datasource.UseWriteDataSourceAspect;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, HikariDataSource.class})
@ConditionalOnMissingBean(DataSource.class)
@ConditionalOnProperty(prefix = "app.datasource.read-write-routing", name = "enabled", havingValue = "true")
public class ReadWriteDataSourceAutoConfiguration {

    @Bean(name = "writeDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "writeDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource writeDataSource(@Qualifier("writeDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "readDataSourceProperties")
    @ConfigurationProperties(prefix = "app.datasource.read")
    public DataSourceProperties readDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "readDataSource")
    @ConfigurationProperties(prefix = "app.datasource.read.hikari")
    public DataSource readDataSource(
            @Qualifier("readDataSourceProperties") DataSourceProperties readProperties,
            @Qualifier("writeDataSourceProperties") DataSourceProperties writeProperties,
            @Qualifier("writeDataSource") DataSource writeDataSource
    ) {
        if (!StringUtils.hasText(readProperties.getUrl())) {
            return writeDataSource;
        }

        if (!StringUtils.hasText(readProperties.getUsername())) {
            readProperties.setUsername(writeProperties.getUsername());
        }
        if (!StringUtils.hasText(readProperties.getPassword())) {
            readProperties.setPassword(writeProperties.getPassword());
        }
        if (!StringUtils.hasText(readProperties.getDriverClassName())) {
            readProperties.setDriverClassName(writeProperties.getDriverClassName());
        }

        return readProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource
    ) {
        TransactionRoutingDataSource routingDataSource = new TransactionRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceRoute.WRITE, writeDataSource);
        targetDataSources.put(DataSourceRoute.READ, readDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    @ConditionalOnClass(UseWriteDataSourceAspect.class)
    @ConditionalOnMissingBean
    public UseWriteDataSourceAspect useWriteDataSourceAspect() {
        return new UseWriteDataSourceAspect();
    }
}
