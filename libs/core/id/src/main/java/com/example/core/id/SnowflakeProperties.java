package com.example.core.id;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Snowflake ID 생성기 설정.
 *
 * app:
 *   snowflake:
 *     datacenter-id: 1   # 0~31 (필수 설정 권장)
 *     worker-id: 1       # 0~31 (필수 설정 권장)
 */
@ConfigurationProperties(prefix = "app.snowflake")
public class SnowflakeProperties {

    private long datacenterId = 0;
    private long workerId = 0;

    @PostConstruct
    void validate() {
        if (datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException(
                    "app.snowflake.datacenter-id must be between 0 and 31, got: " + datacenterId);
        }
        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException(
                    "app.snowflake.worker-id must be between 0 and 31, got: " + workerId);
        }
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(long workerId) {
        this.workerId = workerId;
    }
}
