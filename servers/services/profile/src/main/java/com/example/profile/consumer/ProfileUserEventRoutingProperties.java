package com.example.profile.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "profile.user-events")
public class ProfileUserEventRoutingProperties {

    private RoutingMode routingMode = RoutingMode.DIRECT;

    public RoutingMode getRoutingMode() {
        return routingMode;
    }

    public void setRoutingMode(RoutingMode routingMode) {
        this.routingMode = routingMode;
    }

    public boolean isInboxMode() {
        return routingMode == RoutingMode.INBOX;
    }

    public enum RoutingMode {
        DIRECT,
        INBOX
    }
}
