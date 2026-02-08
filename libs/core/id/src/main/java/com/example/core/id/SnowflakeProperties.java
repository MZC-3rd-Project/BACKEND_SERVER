package com.example.core.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Snowflake ID 생성기 설정.
 *
 * app:
 *   snowflake:
 *     datacenter-id: 1   # 0~31
 *     worker-id: 1       # 0~31
 */
@ConfigurationProperties(prefix = "app.snowflake")
public class SnowflakeProperties {

    private long datacenterId = 1;
    private long workerId = 1;

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
