package com.example.storequery.service.impl;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.projection.StoreReadImageSnapshot;
import com.example.storequery.service.StoreReadModelSnapshotReader;
import com.example.storequery.source.StoreItemSummarySource;
import com.example.storequery.source.StoreItemSummarySourceReader;
import com.example.storequery.source.StoreOwnerSnapshot;
import com.example.storequery.source.StoreOwnerSnapshotSourceReader;
import com.example.storequery.source.StoreSourceSnapshot;
import com.example.storequery.source.StoreSourceSnapshotReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompositeStoreReadModelSnapshotReaderTest {

    @Mock
    private StoreSourceSnapshotReader storeSourceSnapshotReader;

    @Mock
    private StoreOwnerSnapshotSourceReader ownerSnapshotSourceReader;

    @Mock
    private StoreItemSummarySourceReader itemSummarySourceReader;

    private StoreReadModelSnapshotReader snapshotReader;

    @BeforeEach
    void setUp() {
        snapshotReader = new CompositeStoreReadModelSnapshotReader(
            storeSourceSnapshotReader,
            ownerSnapshotSourceReader,
            itemSummarySourceReader
        );
    }

    @Test
    void store_owner_item을_합쳐_projection_snapshot을_만든다() {
        LocalDateTime now = LocalDateTime.now();
        when(storeSourceSnapshotReader.read(1L)).thenReturn(Optional.of(new StoreSourceSnapshot(
            1L,
            100L,
            "MZC Store",
            StoreQueryStatus.ACTIVE,
            "music merch",
            "Seoul",
            null,
            "010-1234-5678",
            null,
            List.of(
                new StoreReadImageSnapshot(StoreQueryImageType.GALLERY, 30L, "https://gallery", 2, now),
                new StoreReadImageSnapshot(StoreQueryImageType.THUMBNAIL, 10L, "https://thumb", 0, now)
            ),
            now.minusDays(3),
            now.minusHours(2)
        )));
        when(ownerSnapshotSourceReader.readByUserId(100L)).thenReturn(Optional.of(new StoreOwnerSnapshot(
            100L,
            "owner",
            "https://owner"
        )));
        when(itemSummarySourceReader.readByStoreId(1L)).thenReturn(List.of(
            new StoreItemSummarySource(1000L, 1L, 100L, "item1", 1000L, "GOODS", "ON_SALE", 101L, "https://i1", now.minusHours(5)),
            new StoreItemSummarySource(1001L, 1L, 100L, "item2", 2000L, "GOODS", "HIDDEN", 102L, "https://i2", now.minusHours(1))
        ));

        var snapshot = snapshotReader.read(1L).orElseThrow();

        assertThat(snapshot.storeName()).isEqualTo("MZC Store");
        assertThat(snapshot.ownerNickname()).isEqualTo("owner");
        assertThat(snapshot.thumbnailMediaId()).isEqualTo(10L);
        assertThat(snapshot.galleryCount()).isEqualTo(1);
        assertThat(snapshot.activeItemCount()).isEqualTo(1);
        assertThat(snapshot.latestItemUpdatedAt()).isEqualTo(now.minusHours(1));
        assertThat(snapshot.searchText()).contains("MZC Store", "music merch", "Seoul", "owner", "item1");
        assertThat(snapshot.searchText()).doesNotContain("item2");
        assertThat(snapshot.images()).hasSize(2);
        assertThat(snapshot.items()).hasSize(2);
    }

    @Test
    void store_source가_없으면_empty를_반환한다() {
        when(storeSourceSnapshotReader.read(9L)).thenReturn(Optional.empty());

        assertThat(snapshotReader.read(9L)).isEmpty();
    }
}
