package com.example.storequery.service;

import java.util.Set;

public interface StoreReadModelProjectionApplicationService {

    void project(Long storeId);

    Set<Long> projectByTrigger(String eventType, String aggregateType, String payloadJson);
}
