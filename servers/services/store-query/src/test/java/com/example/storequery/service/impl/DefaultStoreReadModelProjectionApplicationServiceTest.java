package com.example.storequery.service.impl;

import com.example.storequery.service.StoreReadModelProjector;
import com.example.storequery.service.StoreReadModelTriggerResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStoreReadModelProjectionApplicationServiceTest {

    @Mock
    private StoreReadModelProjector projector;

    @Mock
    private StoreReadModelTriggerResolver triggerResolver;

    private DefaultStoreReadModelProjectionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultStoreReadModelProjectionApplicationService(projector, triggerResolver);
    }

    @Test
    void projectByTrigger_resolvesStoreIdsAndProjectsEachStore() {
        Set<Long> storeIds = new LinkedHashSet<>();
        storeIds.add(11L);
        storeIds.add(22L);
        when(triggerResolver.resolveStoreIds("ITEM_UPDATED", "ITEM", "{\"storeId\":11}")).thenReturn(storeIds);

        Set<Long> projected = service.projectByTrigger("ITEM_UPDATED", "ITEM", "{\"storeId\":11}");

        assertThat(projected).containsExactly(11L, 22L);
        verify(projector).project(11L);
        verify(projector).project(22L);
    }

    @Test
    void project_delegatesToProjector() {
        service.project(33L);

        verify(projector).project(33L);
        verifyNoMoreInteractions(triggerResolver);
    }
}
