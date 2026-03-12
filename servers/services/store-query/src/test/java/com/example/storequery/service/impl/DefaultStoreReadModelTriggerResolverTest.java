package com.example.storequery.service.impl;

import com.example.storequery.source.StoreOwnerStoreIdSourceReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStoreReadModelTriggerResolverTest {

    @Mock
    private StoreOwnerStoreIdSourceReader storeOwnerStoreIdSourceReader;

    private DefaultStoreReadModelTriggerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultStoreReadModelTriggerResolver(new ObjectMapper(), storeOwnerStoreIdSourceReader);
    }

    @Test
    void itemEvent_resolvesSingleStoreIdFromPayload() {
        Set<Long> result = resolver.resolveStoreIds("ITEM_UPDATED", "ITEM", "{\"storeId\":101}");

        assertThat(result).containsExactly(101L);
    }

    @Test
    void profileEvent_resolvesOwnerStoresFromSourceReader() {
        when(storeOwnerStoreIdSourceReader.readStoreIdsByUserId(55L)).thenReturn(List.of(1L, 2L));

        Set<Long> result = resolver.resolveStoreIds("ProfileUpdated", "PROFILE", "{\"userId\":55}");

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    void unsupportedEvent_returnsEmptySet() {
        Set<Long> result = resolver.resolveStoreIds("UNKNOWN_EVENT", "UNKNOWN", "{\"storeId\":1}");

        assertThat(result).isEmpty();
    }
}
