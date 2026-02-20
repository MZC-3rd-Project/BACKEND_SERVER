package com.example.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "search.popular")
public class SearchPopularProperties {

    private int perSecondLimit = 8;
    private int perMinuteLimit = 100;
    private int repeatPenaltyThreshold = 5;
    private int repeatRejectThreshold = 20;
    private List<String> blockedKeywords = new ArrayList<>();

    public int getPerSecondLimit() {
        return perSecondLimit;
    }

    public void setPerSecondLimit(int perSecondLimit) {
        this.perSecondLimit = perSecondLimit;
    }

    public int getPerMinuteLimit() {
        return perMinuteLimit;
    }

    public void setPerMinuteLimit(int perMinuteLimit) {
        this.perMinuteLimit = perMinuteLimit;
    }

    public int getRepeatPenaltyThreshold() {
        return repeatPenaltyThreshold;
    }

    public void setRepeatPenaltyThreshold(int repeatPenaltyThreshold) {
        this.repeatPenaltyThreshold = repeatPenaltyThreshold;
    }

    public int getRepeatRejectThreshold() {
        return repeatRejectThreshold;
    }

    public void setRepeatRejectThreshold(int repeatRejectThreshold) {
        this.repeatRejectThreshold = repeatRejectThreshold;
    }

    public List<String> getBlockedKeywords() {
        return blockedKeywords;
    }

    public void setBlockedKeywords(List<String> blockedKeywords) {
        this.blockedKeywords = blockedKeywords;
    }
}
