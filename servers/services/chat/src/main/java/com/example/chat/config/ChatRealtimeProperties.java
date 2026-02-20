package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.realtime")
public class ChatRealtimeProperties {

    private String redisTopic = "chat-room-events";
    private boolean redisFanoutEnabled = true;
    private int replayBatchSize = 100;
    private long heartbeatTimeoutSeconds = 60;
    private long heartbeatCheckIntervalMs = 10000;

    public String getRedisTopic() {
        return redisTopic;
    }

    public void setRedisTopic(String redisTopic) {
        this.redisTopic = redisTopic;
    }

    public boolean isRedisFanoutEnabled() {
        return redisFanoutEnabled;
    }

    public void setRedisFanoutEnabled(boolean redisFanoutEnabled) {
        this.redisFanoutEnabled = redisFanoutEnabled;
    }

    public int getReplayBatchSize() {
        return replayBatchSize;
    }

    public void setReplayBatchSize(int replayBatchSize) {
        this.replayBatchSize = replayBatchSize;
    }

    public long getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public long getHeartbeatCheckIntervalMs() {
        return heartbeatCheckIntervalMs;
    }

    public void setHeartbeatCheckIntervalMs(long heartbeatCheckIntervalMs) {
        this.heartbeatCheckIntervalMs = heartbeatCheckIntervalMs;
    }
}
