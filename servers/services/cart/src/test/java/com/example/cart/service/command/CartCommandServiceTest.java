package com.example.cart.service.command;

import com.example.cart.client.CartSalesClient;
import com.example.cart.config.CartPolicyProperties;
import com.example.cart.domain.CartLine;
import com.example.cart.domain.CartLineIdentity;
import com.example.cart.dto.request.AddCartItemRequest;
import com.example.cart.dto.request.StartCartCheckoutRequest;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.repository.CartRepository;
import com.example.cart.service.CartSnapshotData;
import com.example.cart.service.CartSnapshotEnricher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartCommandServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartSnapshotEnricher cartSnapshotEnricher;

    @Mock
    private CartSalesClient cartSalesClient;

    private CartPolicyProperties cartPolicyProperties;

    @InjectMocks
    private CartCommandService cartCommandService;

    @Captor
    private ArgumentCaptor<CartLine> cartLineCaptor;

    @Captor
    private ArgumentCaptor<List<CartLine>> cartLinesCaptor;

    @BeforeEach
    void setUp() {
        cartPolicyProperties = new CartPolicyProperties();
        cartPolicyProperties.setTtlDays(30);
        cartCommandService = new CartCommandService(cartRepository, cartSnapshotEnricher, cartSalesClient, cartPolicyProperties);
    }

    @Test
    void addItem_enrichesSnapshot_andPersistsMergedLine() {
        AddCartItemRequest request = new AddCartItemRequest();
        ReflectionTestUtils.setField(request, "itemId", 101L);
        ReflectionTestUtils.setField(request, "referenceId", 1001L);
        ReflectionTestUtils.setField(request, "channelType", "normal");
        ReflectionTestUtils.setField(request, "channelRefId", null);
        ReflectionTestUtils.setField(request, "stockItemType", "item_option");
        ReflectionTestUtils.setField(request, "quantity", 2);
        ReflectionTestUtils.setField(request, "selected", true);

        when(cartRepository.findAllByUserId(1L)).thenReturn(List.of());
        when(cartSnapshotEnricher.enrich(101L)).thenReturn(
                new CartSnapshotData(77L, "MZC shirt", null, "MZC store", 12000L, "ON_SALE")
        );

        CartResponse response = cartCommandService.addItem(1L, request);

        verify(cartRepository).save(cartLineCaptor.capture(), eq(1L));
        CartLine saved = cartLineCaptor.getValue();
        assertThat(saved.getIdentity()).isEqualTo(CartLineIdentity.of(101L, 1001L, "NORMAL", null));
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getStoreId()).isEqualTo(77L);
        assertThat(saved.getItemTitle()).isEqualTo("MZC shirt");
        assertThat(response.getItemCount()).isEqualTo(1);
        assertThat(response.getItems().get(0).getStoreName()).isEqualTo("MZC store");
    }

    @Test
    void startCheckout_usesSelectedLinesOnly() {
        StartCartCheckoutRequest request = new StartCartCheckoutRequest();
        ReflectionTestUtils.setField(request, "idempotencyKey", "idem-1");

        CartLine selected = CartLine.create(
                CartLineIdentity.of(101L, 1001L, "NORMAL", null),
                "ITEM_OPTION",
                2,
                true,
                77L,
                "MZC shirt",
                null,
                "MZC store",
                12000L,
                "ON_SALE",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(3600).getEpochSecond()
        );
        CartLine notSelected = CartLine.create(
                CartLineIdentity.of(202L, 2002L, "FUNDING", 88L),
                "ITEM_OPTION",
                1,
                false,
                88L,
                "Funding item",
                null,
                "Funding store",
                5000L,
                "FUNDING",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(3600).getEpochSecond()
        );

        CartCheckoutReservationResponse expected = CartCheckoutReservationResponse.builder()
                .orderId(999L)
                .build();

        when(cartRepository.findAllByUserId(1L)).thenReturn(List.of(selected, notSelected));
        when(cartSalesClient.reserve(eq(1L), eq("idem-1"), any())).thenReturn(expected);

        CartCheckoutReservationResponse response = cartCommandService.startCheckout(1L, request);

        verify(cartSalesClient).reserve(eq(1L), eq("idem-1"), cartLinesCaptor.capture());
        assertThat(cartLinesCaptor.getValue()).hasSize(1);
        assertThat(cartLinesCaptor.getValue().get(0).getIdentity()).isEqualTo(CartLineIdentity.of(101L, 1001L, "NORMAL", null));
        assertThat(response.getOrderId()).isEqualTo(999L);
    }
}
