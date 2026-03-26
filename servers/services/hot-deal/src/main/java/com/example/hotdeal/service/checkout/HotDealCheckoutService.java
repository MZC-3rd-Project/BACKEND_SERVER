package com.example.hotdeal.service.checkout;

import com.example.clients.order.dto.OrderCreateLineItem;
import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.exception.OrderClientConflictException;
import com.example.clients.order.exception.OrderClientException;
import com.example.clients.order.facade.OrderCreateClientFacade;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.core.exception.BusinessException;
import com.example.core.id.Snowflake;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.hotdeal.dto.HotDealCheckoutCancelRequest;
import com.example.hotdeal.dto.HotDealCheckoutCancelResponse;
import com.example.hotdeal.dto.HotDealCheckoutReserveRequest;
import com.example.hotdeal.dto.HotDealCheckoutReserveResponse;
import com.example.hotdeal.dto.HotDealCheckoutSubmitRequest;
import com.example.hotdeal.dto.HotDealCheckoutSubmitResponse;
import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.event.HotDealPurchasedEvent;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotDealCheckoutService {

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final String PURCHASED_KEY_PREFIX = "hotdeal:purchased:";
    private static final String CHECKOUT_RESERVATION_KEY_PREFIX = "hotdeal:checkout:reservation:";
    private static final String DETAIL_CACHE_KEY_PREFIX = "hotdeal:detail:";
    private static final long RESERVATION_TTL_MINUTES = 10;

    private static final String RESERVE_SCRIPT = """
            local stockKey = KEYS[1]
            local purchasedKey = KEYS[2]
            local reservationKey = KEYS[3]
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local orderId = ARGV[3]
            local maxPerUser = tonumber(ARGV[4])

            local purchased = tonumber(redis.call('GET', purchasedKey) or '0')
            if purchased == nil then purchased = 0 end
            if purchased + quantity > maxPerUser then
                return -2
            end

            local existingReservation = redis.call('GET', reservationKey)
            if existingReservation ~= false then
                return -3
            end

            local current = tonumber(redis.call('GET', stockKey))
            if current == nil then
                return -1
            end
            if current < quantity then
                return 0
            end

            redis.call('DECRBY', stockKey, quantity)
            redis.call('SET', reservationKey, orderId, 'EX', ttl)
            return 1
            """;

    private static final String CANCEL_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local orderId = ARGV[1]
            local quantity = tonumber(ARGV[2])

            local currentReservation = redis.call('GET', reservationKey)
            if currentReservation == false then
                return 0
            end
            if tostring(currentReservation) ~= tostring(orderId) then
                return -1
            end

            redis.call('INCRBY', stockKey, quantity)
            redis.call('DEL', reservationKey)
            return 1
            """;

    private final HotDealRepository hotDealRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final QueueService queueService;
    private final ProductItemQueryClientFacade productClient;
    private final OrderCreateClientFacade orderCreateClientFacade;
    private final HotDealCheckoutSessionStore checkoutSessionStore;
    private final EventPublisher eventPublisher;
    private final Snowflake snowflake;

    @Transactional
    public HotDealCheckoutReserveResponse reserve(Long hotDealId, HotDealCheckoutReserveRequest request, Long userId) {
        HotDealCheckoutSession existingSession = checkoutSessionStore.findSessionByIdempotency(userId, request.getIdempotencyKey())
                .orElse(null);
        if (existingSession != null) {
            if (!existingSession.getHotDealId().equals(hotDealId) || !existingSession.getQuantity().equals(request.getQuantity())) {
                throw new BusinessException(HotDealErrorCode.CHECKOUT_IDEMPOTENCY_CONFLICT);
            }
            if (!existingSession.isTerminal() && !existingSession.isExpired(LocalDateTime.now())) {
                return HotDealCheckoutReserveResponse.builder()
                        .orderId(existingSession.getOrderId())
                        .expiresAt(existingSession.getExpiresAt())
                        .build();
            }
        }

        validateQueueAdmission(hotDealId, userId, request.getToken());

        HotDeal hotDeal = findActiveHotDeal(hotDealId);
        int maxPerUser = resolveMaxPerUser(hotDealId, hotDeal.getMaxPerUser());
        validateRequestedQuantity(request.getQuantity(), maxPerUser);

        JsonNode product = findProductSnapshot(hotDeal.getItemId());
        Long orderId = snowflake.nextId();
        int result = executeReserveScript(hotDealId, userId, request.getQuantity(), orderId, maxPerUser);
        if (result == -1) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND);
        }
        if (result == -2) {
            throw new BusinessException(HotDealErrorCode.PURCHASE_QUANTITY_EXCEEDED);
        }
        if (result == -3) {
            throw new BusinessException(HotDealErrorCode.CHECKOUT_ALREADY_RESERVED);
        }
        if (result == 0) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES);
        HotDealCheckoutSession session = HotDealCheckoutSession.builder()
                .orderId(orderId)
                .hotDealId(hotDealId)
                .itemId(hotDeal.getItemId())
                .userId(userId)
                .sellerId(nullableLong(product, "sellerId"))
                .storeId(nullableLong(product, "storeId"))
                .itemType(nullableText(product, "itemType"))
                .title(hotDeal.getTitle())
                .quantity(request.getQuantity())
                .unitPrice(hotDeal.getDiscountedPrice())
                .totalAmount(hotDeal.getDiscountedPrice() * request.getQuantity())
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(expiresAt)
                .status(HotDealCheckoutSessionStatus.RESERVED)
                .build();
        checkoutSessionStore.save(session);
        queueService.consumeAdmission(hotDealId, userId);

        log.info("Hot-deal checkout reserved. hotDealId={}, orderId={}, userId={}, quantity={}",
                hotDealId, orderId, userId, request.getQuantity());

        return HotDealCheckoutReserveResponse.builder()
                .orderId(orderId)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public HotDealCheckoutSubmitResponse submit(HotDealCheckoutSubmitRequest request, Long userId) {
        HotDealCheckoutSession session = getOwnedSession(request.getOrderId(), userId);
        validateSubmittable(session);

        if (session.getStatus() == HotDealCheckoutSessionStatus.ORDER_CREATED) {
            return HotDealCheckoutSubmitResponse.builder()
                    .orderId(session.getOrderId())
                    .status(session.getStatus().name())
                    .build();
        }

        try {
            orderCreateClientFacade.createOrder(toOrderCreateRequest(session, request));
        } catch (OrderClientConflictException exception) {
            log.info("Hot-deal checkout submit replayed on existing order. orderId={}", session.getOrderId());
        } catch (OrderClientException exception) {
            throw new BusinessException(HotDealErrorCode.ORDER_SERVICE_ERROR);
        }

        HotDealCheckoutSession orderCreatedSession = session.markOrderCreated(LocalDateTime.now());
        checkoutSessionStore.save(orderCreatedSession);
        return HotDealCheckoutSubmitResponse.builder()
                .orderId(orderCreatedSession.getOrderId())
                .status(orderCreatedSession.getStatus().name())
                .build();
    }

    @Transactional
    public HotDealCheckoutCancelResponse cancel(HotDealCheckoutCancelRequest request, Long userId) {
        HotDealCheckoutSession session = getOwnedSession(request.getOrderId(), userId);
        if (session.getStatus() != HotDealCheckoutSessionStatus.RESERVED) {
            return HotDealCheckoutCancelResponse.builder()
                    .orderId(session.getOrderId())
                    .status(session.getStatus().name())
                    .build();
        }

        releaseReservation(session, HotDealCheckoutSessionStatus.CANCELLED);
        return HotDealCheckoutCancelResponse.builder()
                .orderId(session.getOrderId())
                .status(HotDealCheckoutSessionStatus.CANCELLED.name())
                .build();
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        HotDealCheckoutSession session = checkoutSessionStore.findSession(orderId).orElse(null);
        if (session == null || session.getStatus() == HotDealCheckoutSessionStatus.CONFIRMED) {
            return;
        }
        if (session.getStatus() == HotDealCheckoutSessionStatus.CANCELLED || session.getStatus() == HotDealCheckoutSessionStatus.EXPIRED) {
            return;
        }

        int updated = hotDealRepository.incrementSoldQuantity(session.getHotDealId(), session.getQuantity());
        if (updated == 0) {
            log.warn("Hot-deal sold quantity increment skipped. hotDealId={}, orderId={}",
                    session.getHotDealId(), session.getOrderId());
        }

        stringRedisTemplate.opsForValue().increment(purchasedKey(session.getHotDealId(), session.getUserId()), session.getQuantity());
        stringRedisTemplate.delete(DETAIL_CACHE_KEY_PREFIX + session.getHotDealId());

        checkoutSessionStore.save(session.markConfirmed());
        eventPublisher.publish(
                new HotDealPurchasedEvent(
                        session.getHotDealId(),
                        session.getOrderId(),
                        session.getUserId(),
                        session.getItemId(),
                        session.getQuantity(),
                        session.getTotalAmount()
                ),
                EventMetadata.of("HotDeal", String.valueOf(session.getHotDealId()))
        );
        log.info("Hot-deal checkout confirmed. hotDealId={}, orderId={}, userId={}",
                session.getHotDealId(), session.getOrderId(), session.getUserId());
    }

    @Transactional
    public void cancelByOrderId(Long orderId) {
        HotDealCheckoutSession session = checkoutSessionStore.findSession(orderId).orElse(null);
        if (session == null) {
            return;
        }
        if (session.getStatus() == HotDealCheckoutSessionStatus.CONFIRMED
                || session.getStatus() == HotDealCheckoutSessionStatus.CANCELLED
                || session.getStatus() == HotDealCheckoutSessionStatus.EXPIRED) {
            return;
        }

        releaseReservation(session, HotDealCheckoutSessionStatus.CANCELLED);
    }

    @Transactional
    public void expireReservedCheckoutSessions() {
        LocalDateTime now = LocalDateTime.now();
        for (Long orderId : checkoutSessionStore.findExpiredReservedOrderIds(now)) {
            HotDealCheckoutSession session = checkoutSessionStore.findSession(orderId).orElse(null);
            if (session == null || session.getStatus() != HotDealCheckoutSessionStatus.RESERVED || !session.isExpired(now)) {
                continue;
            }
            releaseReservation(session, HotDealCheckoutSessionStatus.EXPIRED);
        }
    }

    private void releaseReservation(HotDealCheckoutSession session, HotDealCheckoutSessionStatus terminalStatus) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CANCEL_SCRIPT, Long.class);
        stringRedisTemplate.execute(
                script,
                List.of(stockKey(session.getHotDealId()), checkoutReservationKey(session.getHotDealId(), session.getUserId())),
                String.valueOf(session.getOrderId()),
                String.valueOf(session.getQuantity())
        );
        HotDealCheckoutSession terminalSession = terminalStatus == HotDealCheckoutSessionStatus.EXPIRED
                ? session.markExpired()
                : session.markCancelled();
        checkoutSessionStore.save(terminalSession);
        stringRedisTemplate.delete(DETAIL_CACHE_KEY_PREFIX + session.getHotDealId());
        log.info("Hot-deal checkout released. hotDealId={}, orderId={}, status={}",
                session.getHotDealId(), session.getOrderId(), terminalStatus);
    }

    private HotDealCheckoutSession getOwnedSession(Long orderId, Long userId) {
        HotDealCheckoutSession session = checkoutSessionStore.findSession(orderId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.CHECKOUT_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(HotDealErrorCode.CHECKOUT_SESSION_FORBIDDEN);
        }
        return session;
    }

    private void validateSubmittable(HotDealCheckoutSession session) {
        if (session.isExpired(LocalDateTime.now())) {
            releaseReservation(session, HotDealCheckoutSessionStatus.EXPIRED);
            throw new BusinessException(HotDealErrorCode.RESERVATION_EXPIRED);
        }
        if (session.getStatus() == HotDealCheckoutSessionStatus.CANCELLED
                || session.getStatus() == HotDealCheckoutSessionStatus.EXPIRED) {
            throw new BusinessException(HotDealErrorCode.RESERVATION_EXPIRED);
        }
        if (session.getStatus() == HotDealCheckoutSessionStatus.CONFIRMED) {
            throw new BusinessException(HotDealErrorCode.CHECKOUT_ALREADY_COMPLETED);
        }
    }

    private OrderCreateRequest toOrderCreateRequest(HotDealCheckoutSession session, HotDealCheckoutSubmitRequest request) {
        return new OrderCreateRequest(
                session.getOrderId(),
                session.getUserId(),
                session.getExpiresAt(),
                session.getTotalAmount(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getDeliveryAddressId(),
                request.getDeliveryMemo(),
                List.of(new OrderCreateLineItem(
                        "HOT_DEAL",
                        session.getHotDealId(),
                        session.getItemId(),
                        session.getItemType(),
                        session.getTitle(),
                        session.getSellerId(),
                        session.getStoreId(),
                        null,
                        null,
                        null,
                        session.getQuantity(),
                        session.getUnitPrice(),
                        session.getUnitPrice(),
                        session.getTotalAmount()
                ))
        );
    }

    private int executeReserveScript(Long hotDealId, Long userId, Integer quantity, Long orderId, int maxPerUser) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RESERVE_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                script,
                List.of(
                        stockKey(hotDealId),
                        purchasedKey(hotDealId, userId),
                        checkoutReservationKey(hotDealId, userId)
                ),
                String.valueOf(quantity),
                String.valueOf(RESERVATION_TTL_MINUTES * 60),
                String.valueOf(orderId),
                String.valueOf(maxPerUser)
        );
        return result == null ? -1 : result.intValue();
    }

    private void validateQueueAdmission(Long hotDealId, Long userId, String token) {
        if (!queueService.isAdmitted(hotDealId, userId)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_NOT_ADMITTED);
        }
        if (!queueService.isTokenValid(hotDealId, userId, token)) {
            throw new BusinessException(HotDealErrorCode.QUEUE_TOKEN_INVALID);
        }
    }

    private HotDeal findActiveHotDeal(Long hotDealId) {
        HotDeal hotDeal = hotDealRepository.findById(hotDealId)
                .orElseThrow(() -> new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND));
        if (hotDeal.getStatus() != HotDealStatus.ACTIVE) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_ACTIVE);
        }
        return hotDeal;
    }

    private int resolveMaxPerUser(Long hotDealId, int fallbackMaxPerUser) {
        String maxPerUserValue = stringRedisTemplate.opsForValue().get("hotdeal:maxperuser:" + hotDealId);
        if (maxPerUserValue == null) {
            return fallbackMaxPerUser;
        }
        try {
            return Integer.parseInt(maxPerUserValue);
        } catch (NumberFormatException exception) {
            log.warn("Failed to parse hot-deal maxPerUser override. hotDealId={}, rawValue={}", hotDealId, maxPerUserValue);
            return fallbackMaxPerUser;
        }
    }

    private void validateRequestedQuantity(Integer quantity, int maxPerUser) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(HotDealErrorCode.PURCHASE_QUANTITY_EXCEEDED);
        }
        if (quantity > maxPerUser) {
            throw new BusinessException(HotDealErrorCode.PURCHASE_QUANTITY_EXCEEDED);
        }
    }

    private JsonNode findProductSnapshot(Long itemId) {
        try {
            return productClient.findItem(itemId);
        } catch (ProductClientException exception) {
            throw new BusinessException(HotDealErrorCode.PRODUCT_SERVICE_ERROR);
        }
    }

    private Long nullableLong(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asLong();
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private String stockKey(Long hotDealId) {
        return STOCK_KEY_PREFIX + hotDealId;
    }

    private String purchasedKey(Long hotDealId, Long userId) {
        return PURCHASED_KEY_PREFIX + hotDealId + ":" + userId;
    }

    private String checkoutReservationKey(Long hotDealId, Long userId) {
        return CHECKOUT_RESERVATION_KEY_PREFIX + hotDealId + ":" + userId;
    }
}
