package com.example.storequery.service.impl;

import com.example.storequery.service.StoreReadModelProjectionApplicationService;
import com.example.storequery.service.StoreReadModelProjector;
import com.example.storequery.service.StoreReadModelTriggerResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultStoreReadModelProjectionApplicationService implements StoreReadModelProjectionApplicationService {

    private final StoreReadModelProjector projector;
    private final StoreReadModelTriggerResolver triggerResolver;

    @Override
    public void project(Long storeId) {
        projector.project(storeId);
    }

    @Override
    public Set<Long> projectByTrigger(String eventType, String aggregateType, String payloadJson) {
        Set<Long> storeIds = triggerResolver.resolveStoreIds(eventType, aggregateType, payloadJson);
        storeIds.forEach(projector::project);
        return storeIds;
    }
}
