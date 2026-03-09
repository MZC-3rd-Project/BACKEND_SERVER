package com.example.event.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.event.consumer")
public class EventConsumerRoutingProperties {

    private final Map<String, RoutingProperties> routing = new HashMap<>();

    public Map<String, RoutingProperties> getRouting() {
        return routing;
    }

    public static class RoutingProperties {
        private ConsumerRoutingMode mode = ConsumerRoutingMode.DIRECT;

        public ConsumerRoutingMode getMode() {
            return mode;
        }

        public void setMode(ConsumerRoutingMode mode) {
            this.mode = mode;
        }
    }
}
