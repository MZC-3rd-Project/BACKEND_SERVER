package com.example.sales.service.query;

import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.dto.ProductQuotedLineItem;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.example.core.exception.BusinessException;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.CheckoutQuoteCache;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionLineItem;
import com.example.sales.entity.CheckoutSessionStatus;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.CheckoutSessionRepository;
import com.example.sales.service.command.CheckoutDraftRedisService;
import com.example.sales.service.command.CheckoutQuoteCacheRedisService;
import com.example.sales.service.support.CheckoutSnapshotAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutQuoteQueryServiceTest {

    @Mock
    private ProductQuoteClientFacade productQuoteClientFacade;

    @Mock
    private CheckoutDraftRedisService checkoutDraftRedisService;

    @Mock
    private CheckoutQuoteCacheRedisService checkoutQuoteCacheRedisService;

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CheckoutSnapshotAssembler checkoutSnapshotAssembler = new CheckoutSnapshotAssembler();

    private CheckoutQuoteQueryService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutQuoteQueryService(
                productQuoteClientFacade,
                checkoutDraftRedisService,
                checkoutQuoteCacheRedisService,
                checkoutSessionRepository,
                checkoutSnapshotAssembler
        );
    }

    @Test
    void quote_returnsCachedQuoteWhenRedisQuoteCacheHits() throws Exception {
        CheckoutQuoteRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutQuoteRequest.class);
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
                LocalDateTime.now().plusMinutes(10)
        );
        CheckoutDraft draft = CheckoutDraft.builder()
                .orderId(12345L)
                .userId(1001L)
                .idempotencyKey("idem-2")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .lineItems(List.of(CheckoutDraft.LineItem.builder()
                        .itemId(930001L)
                        .channelType("NORMAL")
                        .channelRefId(null)
                        .stockItemType("ITEM_OPTION")
                        .referenceId(930101L)
                        .quantity(1)
                        .build()))
                .build();
        CheckoutQuoteCache cachedQuote = CheckoutQuoteCache.builder()
                .orderId(12345L)
                .expiresAt(draft.getExpiresAt())
                .quotedAt(LocalDateTime.now())
                .totalAmount(12000L)
                .lineItems(List.of(CheckoutQuoteCache.LineItem.builder()
                        .itemId(930001L)
                        .itemType("GOODS")
                        .title("MZC 티셔츠")
                        .sellerId(88L)
                        .storeId(11L)
                        .referenceId(930101L)
                        .referenceName("Blue / L")
                        .stockItemType("ITEM_OPTION")
                        .quantity(1)
                        .baseUnitPrice(10000L)
                        .finalUnitPrice(12000L)
                        .lineAmount(12000L)
                        .build()))
                .build();

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(checkoutDraftRedisService.findDraft(12345L)).thenReturn(Optional.of(draft));
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.of(cachedQuote));

        CheckoutQuoteResponse response = service.quote(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getTotalAmount()).isEqualTo(12000L);
        assertThat(response.getLineItems()).hasSize(1);
        assertThat(response.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");

        verify(productQuoteClientFacade, never()).quoteItems(any());
    }

    @Test
    void quote_usesPersistedSessionSnapshotWhenQuoteCacheMisses() throws Exception {
        CheckoutQuoteRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutQuoteRequest.class);
        CheckoutSession session = reservedSession(12345L, 1001L, "idem-2");
        session.getLineItems().get(0).applyQuoteSnapshot(
                "GOODS",
                "MZC 티셔츠",
                88L,
                11L,
                "Blue / L",
                10000L,
                12000L,
                12000L
        );
        session.markQuoted(LocalDateTime.now());

        when(checkoutDraftRedisService.findDraft(12345L)).thenReturn(Optional.empty());
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));

        CheckoutQuoteResponse response = service.quote(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getTotalAmount()).isEqualTo(12000L);
        assertThat(response.getLineItems()).hasSize(1);
        assertThat(response.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");

        verify(productQuoteClientFacade, never()).quoteItems(any());
        verify(checkoutDraftRedisService).saveDraft(any(CheckoutDraft.class), any(Duration.class));
        verify(checkoutQuoteCacheRedisService).saveQuote(any(CheckoutQuoteCache.class), any(Duration.class));
    }

    @Test
    void quote_usesLiveQuoteWhenNoCachedOrPersistedSnapshotExists() throws Exception {
        CheckoutQuoteRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutQuoteRequest.class);
        CheckoutSession session = reservedSession(12345L, 1001L, "idem-2");
        ProductQuoteResponse quoteResponse = quoteResponse();

        when(checkoutDraftRedisService.findDraft(12345L)).thenReturn(Optional.empty());
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(productQuoteClientFacade.quoteItems(any())).thenReturn(quoteResponse);

        CheckoutQuoteResponse response = service.quote(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getTotalAmount()).isEqualTo(12000L);
        assertThat(response.getLineItems()).hasSize(1);
        assertThat(response.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.QUOTED);
        assertThat(session.getQuotedAt()).isEqualTo(quoteResponse.quotedAt());
        assertThat(session.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");
        assertThat(session.getLineItems().get(0).getFinalUnitPrice()).isEqualTo(12000L);

        ArgumentCaptor<ProductQuoteRequest> quoteRequestCaptor = ArgumentCaptor.forClass(ProductQuoteRequest.class);
        verify(productQuoteClientFacade).quoteItems(quoteRequestCaptor.capture());
        assertThat(quoteRequestCaptor.getValue().lineItems()).hasSize(1);
        assertThat(quoteRequestCaptor.getValue().lineItems().get(0).channelType()).isEqualTo("NORMAL");
        assertThat(quoteRequestCaptor.getValue().lineItems().get(0).channelRefId()).isNull();

        verify(checkoutDraftRedisService).saveDraft(any(CheckoutDraft.class), any(Duration.class));
        verify(checkoutQuoteCacheRedisService).saveQuote(any(CheckoutQuoteCache.class), any(Duration.class));
    }

    @Test
    void warmQuoteCache_populatesCacheAndSnapshot() {
        CheckoutSession session = reservedSession(12345L, 1001L, "idem-2");
        ProductQuoteResponse quoteResponse = quoteResponse();

        when(checkoutDraftRedisService.findDraft(12345L)).thenReturn(Optional.empty());
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(productQuoteClientFacade.quoteItems(any())).thenReturn(quoteResponse);

        service.warmQuoteCache(12345L);

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.QUOTED);
        assertThat(session.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");
        verify(checkoutQuoteCacheRedisService).saveQuote(any(CheckoutQuoteCache.class), any(Duration.class));
    }

    @Test
    void quote_rejectsCancelledSession() throws Exception {
        CheckoutQuoteRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutQuoteRequest.class);
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
                LocalDateTime.now().plusMinutes(10)
        );
        session.cancel();

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.quote(request, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.CHECKOUT_SESSION_CANCELLED);

        verify(checkoutDraftRedisService, never()).findDraft(12345L);
        verify(checkoutQuoteCacheRedisService, never()).findQuote(12345L);
    }

    @Test
    void quote_rejectsExpiredSessionAndClearsCaches() throws Exception {
        CheckoutQuoteRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutQuoteRequest.class);
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-expired",
                LocalDateTime.now().minusMinutes(1)
        );

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.quote(request, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.CHECKOUT_SESSION_EXPIRED);

        verify(checkoutDraftRedisService).deleteDraft(12345L, 1001L, "idem-expired");
        verify(checkoutQuoteCacheRedisService).deleteQuote(12345L);
        verify(checkoutDraftRedisService, never()).findDraft(12345L);
    }

    @Test
    void ensureSubmitQuoteSnapshot_appliesCachedQuoteToReservedSession() {
        CheckoutSession session = reservedSession(12345L, 1001L, "idem-2");
        CheckoutQuoteCache cachedQuote = CheckoutQuoteCache.builder()
                .orderId(12345L)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .quotedAt(LocalDateTime.now())
                .totalAmount(12000L)
                .lineItems(List.of(CheckoutQuoteCache.LineItem.builder()
                        .itemId(930001L)
                        .itemType("GOODS")
                        .title("MZC 티셔츠")
                        .sellerId(88L)
                        .storeId(11L)
                        .referenceId(930101L)
                        .referenceName("Blue / L")
                        .stockItemType("ITEM_OPTION")
                        .quantity(1)
                        .baseUnitPrice(10000L)
                        .finalUnitPrice(12000L)
                        .lineAmount(12000L)
                        .build()))
                .build();

        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.of(cachedQuote));

        service.ensureSubmitQuoteSnapshot(session);

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.QUOTED);
        assertThat(session.getLineItems().get(0).getTitle()).isEqualTo("MZC 티셔츠");
        assertThat(session.getLineItems().get(0).getFinalUnitPrice()).isEqualTo(12000L);
        verify(productQuoteClientFacade, never()).quoteItems(any());
    }

    private CheckoutSession reservedSession(Long orderId, Long userId, String idempotencyKey) {
        CheckoutSession session = CheckoutSession.createReserved(
                orderId,
                userId,
                idempotencyKey,
                LocalDateTime.now().plusMinutes(10)
        );
        session.addLineItem(CheckoutSessionLineItem.createReserved(
                1,
                "NORMAL",
                null,
                930001L,
                "ITEM_OPTION",
                930101L,
                1
        ));
        return session;
    }

    private ProductQuoteResponse quoteResponse() {
        return new ProductQuoteResponse(
                LocalDateTime.now(),
                12000L,
                List.of(new ProductQuotedLineItem(
                        930001L,
                        "GOODS",
                        "MZC 티셔츠",
                        88L,
                        11L,
                        930101L,
                        "Blue / L",
                        "ITEM_OPTION",
                        1,
                        10000L,
                        12000L,
                        12000L
                ))
        );
    }
}
