package com.example.storequery.service.impl;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.entity.StoreReadImage;
import com.example.storequery.entity.StoreReadItem;
import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.projection.StoreReadImageSnapshot;
import com.example.storequery.projection.StoreReadItemSnapshot;
import com.example.storequery.projection.StoreReadModelSnapshot;
import com.example.storequery.repository.StoreReadImageRepository;
import com.example.storequery.repository.StoreReadItemRepository;
import com.example.storequery.repository.StoreReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaStoreReadModelWriterTest {

    @Mock
    private StoreReadModelRepository storeReadModelRepository;

    @Mock
    private StoreReadImageRepository storeReadImageRepository;

    @Mock
    private StoreReadItemRepository storeReadItemRepository;

    private JpaStoreReadModelWriter writer;

    @BeforeEach
    void setUp() {
        writer = new JpaStoreReadModelWriter(
            storeReadModelRepository,
            storeReadImageRepository,
            storeReadItemRepository
        );
    }

    @Test
    void 기존_row가_없으면_새_readModel과_child를_저장한다() {
        StoreReadModelSnapshot snapshot = snapshot();
        when(storeReadModelRepository.findById(1L)).thenReturn(Optional.empty());

        writer.upsert(snapshot);

        ArgumentCaptor<StoreReadModel> modelCaptor = ArgumentCaptor.forClass(StoreReadModel.class);
        verify(storeReadModelRepository).save(modelCaptor.capture());
        verify(storeReadImageRepository).softDeleteActiveByStoreId(1L);
        verify(storeReadItemRepository).softDeleteActiveByStoreId(1L);

        StoreReadModel saved = modelCaptor.getValue();
        assertThat(saved.getStoreId()).isEqualTo(1L);
        assertThat(saved.getOwnerNickname()).isEqualTo("owner");
        assertThat(saved.getActiveItemCount()).isEqualTo(2);

        ArgumentCaptor<List<StoreReadImage>> imageCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeReadImageRepository).saveAll(imageCaptor.capture());
        assertThat(imageCaptor.getValue()).hasSize(2);

        ArgumentCaptor<List<StoreReadItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeReadItemRepository).saveAll(itemCaptor.capture());
        assertThat(itemCaptor.getValue()).hasSize(2);
    }

    @Test
    void softDeleted_row가_있으면_restore후_갱신한다() {
        LocalDateTime now = LocalDateTime.now().minusDays(1);
        StoreReadModel existing = StoreReadModel.of(
            1L,
            100L,
            "old",
            "old-owner",
            null,
            StoreQueryStatus.ACTIVE,
            "old-desc",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            null,
            "old",
            now.minusDays(1),
            now,
            now
        );
        existing.softDelete();

        when(storeReadModelRepository.findById(1L)).thenReturn(Optional.of(existing));

        writer.upsert(snapshot());

        ArgumentCaptor<StoreReadModel> modelCaptor = ArgumentCaptor.forClass(StoreReadModel.class);
        verify(storeReadModelRepository).save(modelCaptor.capture());
        StoreReadModel saved = modelCaptor.getValue();

        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getStoreName()).isEqualTo("store");
        assertThat(saved.getThumbnailMediaId()).isEqualTo(10L);
    }

    @Test
    void softDeleteByStoreId는_메인과_child를_같이_삭제한다() {
        StoreReadModel existing = StoreReadModel.of(
            1L,
            100L,
            "store",
            "owner",
            null,
            StoreQueryStatus.ACTIVE,
            "desc",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            null,
            "search",
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().minusDays(1)
        );
        when(storeReadModelRepository.findById(1L)).thenReturn(Optional.of(existing));

        writer.softDeleteByStoreId(1L);

        verify(storeReadModelRepository).save(any(StoreReadModel.class));
        verify(storeReadImageRepository).softDeleteActiveByStoreId(1L);
        verify(storeReadItemRepository).softDeleteActiveByStoreId(1L);
    }

    @Test
    void softDeleteByStoreId는_row가_없어도_child삭제는_수행한다() {
        when(storeReadModelRepository.findById(99L)).thenReturn(Optional.empty());

        writer.softDeleteByStoreId(99L);

        verify(storeReadModelRepository, never()).save(any(StoreReadModel.class));
        verify(storeReadImageRepository).softDeleteActiveByStoreId(99L);
        verify(storeReadItemRepository).softDeleteActiveByStoreId(99L);
    }

    private static StoreReadModelSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.now();
        return new StoreReadModelSnapshot(
            1L,
            100L,
            "store",
            "owner",
            "https://owner",
            StoreQueryStatus.ACTIVE,
            "desc",
            "Seoul",
            null,
            "010",
            null,
            10L,
            "https://thumb",
            0,
            1,
            2,
            now,
            "store owner",
            now.minusDays(3),
            now,
            List.of(
                new StoreReadImageSnapshot(com.example.storequery.entity.StoreQueryImageType.THUMBNAIL, 10L, "https://thumb", 0, now),
                new StoreReadImageSnapshot(com.example.storequery.entity.StoreQueryImageType.GALLERY, 11L, "https://gallery", 1, now)
            ),
            List.of(
                new StoreReadItemSnapshot(1000L, 1L, 100L, "item1", 1000L, "GOODS", "ON_SALE", 100L, "https://item1", now),
                new StoreReadItemSnapshot(1001L, 1L, 100L, "item2", 2000L, "GOODS", "ON_SALE", 101L, "https://item2", now)
            )
        );
    }
}
