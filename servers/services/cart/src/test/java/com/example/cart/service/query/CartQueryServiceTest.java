package com.example.cart.service.query;

import com.example.cart.domain.CartLine;
import com.example.cart.domain.CartLineIdentity;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.repository.CartRepository;
import com.example.cart.service.CartSnapshotData;
import com.example.cart.service.CartSnapshotEnricher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartQueryServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartSnapshotEnricher cartSnapshotEnricher;

    @InjectMocks
    private CartQueryService cartQueryService;

    @Captor
    private ArgumentCaptor<List<CartLine>> cartLinesCaptor;

    @Test
    void getCart_backfillsMissingSnapshots_andPersistsUpdatedLines() {
        CartLine missingSnapshot = CartLine.rehydrate(
                CartLineIdentity.of(101L, 1001L, "NORMAL", null),
                "ITEM_OPTION",
                1,
                true,
                77L,
                "MZC shirt",
                null,
                null,
                12000L,
                "ON_SALE",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(3600).getEpochSecond(),
                null
        );
        CartLine existingSnapshot = CartLine.rehydrate(
                CartLineIdentity.of(202L, 2002L, "NORMAL", null),
                "ITEM_OPTION",
                1,
                true,
                88L,
                "MZC hoodie",
                "https://cdn.example.com/hoodie.png",
                "MZC store",
                22000L,
                "ON_SALE",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(3600).getEpochSecond(),
                null
        );

        when(cartRepository.findAllByUserId(1L)).thenReturn(List.of(missingSnapshot, existingSnapshot));
        when(cartSnapshotEnricher.enrich(101L)).thenReturn(
                new CartSnapshotData(77L, "MZC shirt", "https://cdn.example.com/shirt.png", "MZC store", 12000L, "ON_SALE")
        );

        CartResponse response = cartQueryService.getCart(1L);

        verify(cartRepository).saveAll(cartLinesCaptor.capture(), org.mockito.ArgumentMatchers.eq(1L));
        assertThat(cartLinesCaptor.getValue()).hasSize(1);
        assertThat(cartLinesCaptor.getValue().get(0).getThumbnailUrl()).isEqualTo("https://cdn.example.com/shirt.png");
        assertThat(response.getItems()).extracting("thumbnailUrl")
                .contains("https://cdn.example.com/shirt.png", "https://cdn.example.com/hoodie.png");
    }
}
