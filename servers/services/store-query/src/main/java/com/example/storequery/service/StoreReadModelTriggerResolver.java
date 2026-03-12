package com.example.storequery.service;

import java.util.Set;

public interface StoreReadModelTriggerResolver {

    Set<Long> resolveStoreIds(String eventType, String aggregateType, String payloadJson);
}
