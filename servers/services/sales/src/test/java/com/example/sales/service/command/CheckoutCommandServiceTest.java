package com.example.sales.service.command;

import com.example.clients.order.dto.OrderCreateResponse;
import com.example.clients.order.exception.OrderClientConflictException;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.exception.OrderClientRetriableException;
import com.example.clients.order.exception.OrderClientValidationException;
import com.example.clients.order.facade.OrderCreateClientFacade;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.dto.ProductQuotedLineItem;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.example.clients.stock.dto.ReserveOrderStockResponse;
import com.example.clients.stock.dto.ReservedOrderStockLineItem;
import com.example.clients.stock.facade.StockOrderReservationClientFacade;
import com.example.clients.stock.facade.StockReservationClientFacade;
import com.example.core.exception.BusinessException;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.CheckoutQuoteCache;
import com.example.sales.dto.checkout.request.CheckoutCancelRequest;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.request.CheckoutReserveRequest;
import com.example.sales.dto.checkout.request.CheckoutSubmitRequest;
import com.example.sales.dto.checkout.response.CheckoutCancelResponse;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.dto.checkout.response.CheckoutReserveResponse;
import com.example.sales.dto.checkout.response.CheckoutSubmitResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionLineItem;
import com.example.sales.entity.CheckoutSessionStatus;
import com.example.sales.entity.CheckoutSubmitAttempt;
import com.example.sales.entity.CheckoutSubmitAttemptStatus;
import com.example.sales.event.CheckoutReservedEvent;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.CheckoutSessionRepository;
import com.example.sales.repository.CheckoutSubmitAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
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
class CheckoutCommandServiceTest {

    @Mock
    private StockOrderReservationClientFacade stockOrderReservationClientFacade;

    @Mock
    private OrderCreateClientFacade orderCreateClientFacade;

    @Mock
    private CheckoutSubmitFailurePolicy checkoutSubmitFailurePolicy;

    @Mock
    private StockReservationClientFacade stockReservationClientFacade;

    @Mock
    private ProductQuoteClientFacade productQuoteClientFacade;

    @Mock
    private CheckoutDraftRedisService checkoutDraftRedisService;

    @Mock
    private CheckoutQuoteCacheRedisService checkoutQuoteCacheRedisService;

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private CheckoutSubmitAttemptRepository checkoutSubmitAttemptRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private CheckoutCommandService service;

    @BeforeEach
    void setUp() {
        service = new CheckoutCommandService(
                orderCreateClientFacade,
                checkoutSubmitFailurePolicy,
                stockOrderReservationClientFacade,
                stockReservationClientFacade,
                productQuoteClientFacade,
                checkoutDraftRedisService,
                checkoutQuoteCacheRedisService,
                checkoutSessionRepository,
                checkoutSubmitAttemptRepository,
                objectMapper,
                applicationEventPublisher
        );
    }

    @Test
    void reserve_savesCheckoutSessionAlongsideRedisDraft() throws Exception {
        CheckoutReserveRequest request = objectMapper.readValue("""
                {
                  "channelType": "NORMAL",
                  "channelRefId": null,
                  "idempotencyKey": "idem-1",
                  "lineItems": [
                    {
                      "itemId": 930001,
                      "stockItemType": "ITEM_OPTION",
                      "referenceId": 930101,
                      "quantity": 2
                    }
                  ]
                }
                """, CheckoutReserveRequest.class);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        ReserveOrderStockResponse stockResponse = new ReserveOrderStockResponse(
                289581624952246272L,
                expiresAt,
                List.of(new ReservedOrderStockLineItem(930001L, "ITEM_OPTION", 930101L, 2))
        );

        when(checkoutDraftRedisService.findOrderIdByIdempotencyKey(1001L, "idem-1"))
                .thenReturn(Optional.empty());
        when(checkoutSessionRepository.findTopByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(1001L, "idem-1"))
                .thenReturn(Optional.empty());
        when(stockOrderReservationClientFacade.reserveOrderStock(any())).thenReturn(stockResponse);

        CheckoutReserveResponse response = service.reserve(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(289581624952246272L);
        assertThat(response.getReservedItems()).hasSize(1);

        ArgumentCaptor<CheckoutDraft> draftCaptor = ArgumentCaptor.forClass(CheckoutDraft.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(checkoutDraftRedisService).saveDraft(draftCaptor.capture(), ttlCaptor.capture());
        assertThat(draftCaptor.getValue().getOrderId()).isEqualTo(289581624952246272L);
        assertThat(draftCaptor.getValue().getLineItems()).hasSize(1);
        assertThat(ttlCaptor.getValue()).isPositive();

        ArgumentCaptor<CheckoutSession> sessionCaptor = ArgumentCaptor.forClass(CheckoutSession.class);
        verify(checkoutSessionRepository).save(sessionCaptor.capture());
        CheckoutSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getOrderId()).isEqualTo(289581624952246272L);
        assertThat(savedSession.getUserId()).isEqualTo(1001L);
        assertThat(savedSession.getLineItems()).hasSize(1);
        assertThat(savedSession.getLineItems().get(0).getChannelType()).isEqualTo("NORMAL");
        assertThat(savedSession.getLineItems().get(0).getReferenceId()).isEqualTo(930101L);

        ArgumentCaptor<CheckoutReservedEvent> eventCaptor = ArgumentCaptor.forClass(CheckoutReservedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(289581624952246272L);
    }

    @Test
    void reserve_reusesPersistedSessionWhenRedisMisses() throws Exception {
        CheckoutReserveRequest request = objectMapper.readValue("""
                {
                  "channelType": "NORMAL",
                  "channelRefId": null,
                  "idempotencyKey": "idem-2",
                  "lineItems": [
                    {
                      "itemId": 930001,
                      "stockItemType": "ITEM_OPTION",
                      "referenceId": 930101,
                      "quantity": 1
                    }
                  ]
                }
                """, CheckoutReserveRequest.class);
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
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

        when(checkoutDraftRedisService.findOrderIdByIdempotencyKey(1001L, "idem-2"))
                .thenReturn(Optional.empty());
        when(checkoutSessionRepository.findTopByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(1001L, "idem-2"))
                .thenReturn(Optional.of(session));

        CheckoutReserveResponse response = service.reserve(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getReservedItems()).hasSize(1);
        assertThat(response.getReservedItems().get(0).getItemId()).isEqualTo(930001L);

        verify(stockOrderReservationClientFacade, never()).reserveOrderStock(any());
        verify(checkoutSessionRepository, never()).save(any());
        verify(checkoutDraftRedisService).saveDraft(any(CheckoutDraft.class), any(Duration.class));
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
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
                .channelType("NORMAL")
                .idempotencyKey("idem-2")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .lineItems(List.of(CheckoutDraft.LineItem.builder()
                        .itemId(930001L)
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
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
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
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
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
        ProductQuoteResponse quoteResponse = new ProductQuoteResponse(
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

        verify(checkoutDraftRedisService).saveDraft(any(CheckoutDraft.class), any(Duration.class));
        verify(checkoutQuoteCacheRedisService).saveQuote(any(CheckoutQuoteCache.class), any(Duration.class));
    }

    @Test
    void warmQuoteCache_populatesCacheAndSnapshot() {
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
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
        ProductQuoteResponse quoteResponse = new ProductQuoteResponse(
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
    void cancel_releasesReservedStockAndClearsCaches() throws Exception {
        CheckoutCancelRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345
                }
                """, CheckoutCancelRequest.class);
        CheckoutSession session = CheckoutSession.createReserved(
                12345L,
                1001L,
                "idem-2",
                LocalDateTime.now().plusMinutes(10)
        );

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));

        CheckoutCancelResponse response = service.cancel(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getStatus()).isEqualTo(CheckoutSessionStatus.CANCELLED);
        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.CANCELLED);
        verify(stockReservationClientFacade).cancelReservationsByOrderId(12345L);
        verify(checkoutDraftRedisService).deleteDraft(12345L, 1001L, "idem-2");
        verify(checkoutQuoteCacheRedisService).deleteQuote(12345L);
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
    void submit_createsOrderAndMarksSessionOrderCreated() throws Exception {
        CheckoutSubmitRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345,
                  "recipientName": "홍길동",
                  "recipientPhone": "01012345678",
                  "deliveryAddressId": 501,
                  "deliveryMemo": "문 앞에 놓아주세요"
                }
                """, CheckoutSubmitRequest.class);
        CheckoutSession session = quotedSession(12345L, 1001L, "idem-submit");

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSubmitAttemptRepository.save(any(CheckoutSubmitAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderCreateClientFacade.createOrder(any())).thenReturn(new OrderCreateResponse(12345L, "CREATED"));

        CheckoutSubmitResponse response = service.submit(request, 1001L);

        assertThat(response.getOrderId()).isEqualTo(12345L);
        assertThat(response.getStatus()).isEqualTo(CheckoutSessionStatus.ORDER_CREATED);
        assertThat(response.getSubmittedAt()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.ORDER_CREATED);
        verify(orderCreateClientFacade).createOrder(any());

        ArgumentCaptor<CheckoutSubmitAttempt> attemptCaptor = ArgumentCaptor.forClass(CheckoutSubmitAttempt.class);
        verify(checkoutSubmitAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(CheckoutSubmitAttemptStatus.SUCCEEDED);
        assertThat(attemptCaptor.getValue().getResponsePayloadJson()).contains("\"status\":\"CREATED\"");
    }

    @Test
    void submit_returnsExistingSuccessWhenOrderAlreadyCreated() throws Exception {
        CheckoutSubmitRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345,
                  "recipientName": "홍길동",
                  "recipientPhone": "01012345678",
                  "deliveryAddressId": 501,
                  "deliveryMemo": "문 앞에 놓아주세요"
                }
                """, CheckoutSubmitRequest.class);
        CheckoutSession session = quotedSession(12345L, 1001L, "idem-submit");
        session.markSubmitting();
        session.markOrderCreated();

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));

        CheckoutSubmitResponse response = service.submit(request, 1001L);

        assertThat(response.getStatus()).isEqualTo(CheckoutSessionStatus.ORDER_CREATED);
        verify(orderCreateClientFacade, never()).createOrder(any());
        verify(checkoutSubmitAttemptRepository, never()).save(any());
    }

    @Test
    void submit_marksFailedWhenOrderClientValidationFails() throws Exception {
        CheckoutSubmitRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345,
                  "recipientName": "홍길동",
                  "recipientPhone": "01012345678",
                  "deliveryAddressId": 501,
                  "deliveryMemo": "문 앞에 놓아주세요"
                }
                """, CheckoutSubmitRequest.class);
        CheckoutSession session = quotedSession(12345L, 1001L, "idem-submit");

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSubmitAttemptRepository.save(any(CheckoutSubmitAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkoutSubmitFailurePolicy.decide(any(OrderClientException.class)))
                .thenReturn(CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.CHECKOUT_SUBMIT_INVALID));
        when(orderCreateClientFacade.createOrder(any()))
                .thenThrow(new OrderClientValidationException("invalid address", "ORDER-006", null));

        assertThatThrownBy(() -> service.submit(request, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.CHECKOUT_SUBMIT_INVALID);

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.CANCELLED);
        verify(stockReservationClientFacade).cancelReservationsByOrderId(12345L);
        verify(checkoutDraftRedisService).deleteDraft(12345L, 1001L, "idem-submit");
        verify(checkoutQuoteCacheRedisService).deleteQuote(12345L);
        ArgumentCaptor<CheckoutSubmitAttempt> attemptCaptor = ArgumentCaptor.forClass(CheckoutSubmitAttempt.class);
        verify(checkoutSubmitAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(CheckoutSubmitAttemptStatus.FAILED);
        assertThat(attemptCaptor.getValue().getLastErrorMessage()).contains("invalid address");
        assertThat(attemptCaptor.getValue().getNextRetryAt()).isNull();
    }

    @Test
    void submit_schedulesRetryWhenOrderClientRetriableFails() throws Exception {
        CheckoutSubmitRequest request = objectMapper.readValue("""
                {
                  "orderId": 12345,
                  "recipientName": "홍길동",
                  "recipientPhone": "01012345678",
                  "deliveryAddressId": 501,
                  "deliveryMemo": "문 앞에 놓아주세요"
                }
                """, CheckoutSubmitRequest.class);
        CheckoutSession session = quotedSession(12345L, 1001L, "idem-submit");

        when(checkoutSessionRepository.findByOrderId(12345L)).thenReturn(Optional.of(session));
        when(checkoutQuoteCacheRedisService.findQuote(12345L)).thenReturn(Optional.empty());
        when(checkoutSubmitAttemptRepository.save(any(CheckoutSubmitAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkoutSubmitFailurePolicy.decide(any(OrderClientException.class)))
                .thenReturn(CheckoutSubmitFailureDecision.cancelImmediately(SalesErrorCode.ORDER_SERVICE_ERROR));
        when(orderCreateClientFacade.createOrder(any()))
                .thenThrow(new OrderClientRetriableException("temporary outage", "ORDER-999", null));

        assertThatThrownBy(() -> service.submit(request, 1001L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SalesErrorCode.ORDER_SERVICE_ERROR);

        assertThat(session.getStatus()).isEqualTo(CheckoutSessionStatus.CANCELLED);
        verify(stockReservationClientFacade).cancelReservationsByOrderId(12345L);
        verify(checkoutDraftRedisService).deleteDraft(12345L, 1001L, "idem-submit");
        verify(checkoutQuoteCacheRedisService).deleteQuote(12345L);
        ArgumentCaptor<CheckoutSubmitAttempt> attemptCaptor = ArgumentCaptor.forClass(CheckoutSubmitAttempt.class);
        verify(checkoutSubmitAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(CheckoutSubmitAttemptStatus.FAILED);
        assertThat(attemptCaptor.getValue().getNextRetryAt()).isNull();
    }

    private CheckoutSession quotedSession(Long orderId, Long userId, String idempotencyKey) {
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
        return session;
    }

}
