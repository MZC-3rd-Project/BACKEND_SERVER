package com.example.product.service.command;

import com.example.product.entity.image.ItemImage;
import com.example.product.repository.ItemImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemThumbnailSyncServiceTest {

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private ItemMediaLinkSyncService itemMediaLinkSyncService;

    @InjectMocks
    private ItemThumbnailSyncService itemThumbnailSyncService;

    @Test
    void syncAfterCommit_forceClearWhenEmpty_callsClear() {
        when(itemImageRepository.findByItemIdOrderBySortOrder(1L)).thenReturn(List.of());

        itemThumbnailSyncService.syncAfterCommit(1L, null, true);

        verify(itemMediaLinkSyncService).clearAfterCommit(1L);
    }

    @Test
    void syncAfterCommit_withoutForceClearAndEmpty_skipsSync() {
        when(itemImageRepository.findByItemIdOrderBySortOrder(1L)).thenReturn(List.of());

        itemThumbnailSyncService.syncAfterCommit(1L, null, false);

        verify(itemMediaLinkSyncService, never()).clearAfterCommit(1L);
        verify(itemMediaLinkSyncService, never()).syncAfterCommit(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void syncAfterCommit_excludesThumbnailFromGallery() {
        ItemImage thumbnail = ItemImage.create(1L, 10L, 0, true);
        ItemImage gallery1 = ItemImage.create(1L, 11L, 1, false);
        ItemImage gallery2 = ItemImage.create(1L, 12L, 2, false);
        when(itemImageRepository.findByItemIdOrderBySortOrder(1L)).thenReturn(List.of(thumbnail, gallery1, gallery2));

        itemThumbnailSyncService.syncAfterCommit(1L, 10L);

        verify(itemMediaLinkSyncService).syncAfterCommit(1L, 10L, List.of(11L, 12L));
    }
}
