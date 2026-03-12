package com.example.storequery.service.impl;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.projection.StoreReadModelSnapshot;
import com.example.storequery.service.StoreReadModelSnapshotReader;
import com.example.storequery.service.StoreReadModelWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultStoreReadModelProjectorTest {

    @Mock
    private StoreReadModelSnapshotReader snapshotReader;

    @Mock
    private StoreReadModelWriter writer;

    private DefaultStoreReadModelProjector projector;

    @BeforeEach
    void setUp() {
        projector = new DefaultStoreReadModelProjector(snapshotReader, writer);
    }

    @Test
    void snapshot이_있으면_upsert를_호출한다() {
        StoreReadModelSnapshot snapshot = snapshot();
        when(snapshotReader.read(10L)).thenReturn(Optional.of(snapshot));

        projector.project(10L);

        verify(writer).upsert(snapshot);
        verifyNoMoreInteractions(writer);
    }

    @Test
    void snapshot이_없으면_softDelete를_호출한다() {
        when(snapshotReader.read(11L)).thenReturn(Optional.empty());

        projector.project(11L);

        verify(writer).softDeleteByStoreId(11L);
        verifyNoMoreInteractions(writer);
    }

    @Test
    void 잘못된_storeId면_예외를_던진다() {
        assertThatThrownBy(() -> projector.project(0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("storeId");
    }

    private static StoreReadModelSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.now();
        return new StoreReadModelSnapshot(
            10L,
            100L,
            "store",
            "owner",
            "https://img",
            StoreQueryStatus.ACTIVE,
            "desc",
            "Seoul",
            null,
            "010",
            null,
            1L,
            "https://thumb",
            0,
            1,
            2,
            now,
            "search",
            now.minusDays(1),
            now,
            List.of(),
            List.of()
        );
    }
}
