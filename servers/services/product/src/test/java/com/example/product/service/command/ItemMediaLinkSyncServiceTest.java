package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.exception.ProductErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemMediaLinkSyncServiceTest {

    @Mock
    private MediaReferenceService mediaReferenceService;

    @InjectMocks
    private ItemMediaLinkSyncService itemMediaLinkSyncService;

    @Test
    void syncAfterCommit_whenSyncFails_shouldNotThrow() {
        Long itemId = 1L;
        doThrow(new BusinessException(ProductErrorCode.MEDIA_SERVICE_ERROR))
                .when(mediaReferenceService)
                .syncItemMediaLinks(itemId, 10L, List.of(20L));

        assertThatCode(() -> itemMediaLinkSyncService.syncAfterCommit(itemId, 10L, List.of(20L)))
                .doesNotThrowAnyException();
    }

    @Test
    void clearAfterCommit_delegatesToMediaReference() {
        Long itemId = 2L;

        itemMediaLinkSyncService.clearAfterCommit(itemId);

        verify(mediaReferenceService).syncItemMediaLinks(itemId, null, List.of());
    }
}
