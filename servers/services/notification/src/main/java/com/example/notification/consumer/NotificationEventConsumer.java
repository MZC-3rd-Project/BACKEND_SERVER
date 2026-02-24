package com.example.notification.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.util.JsonUtils;
import com.example.clients.product.dto.ProductItemSummary;
import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationType;
import com.example.notification.service.command.NotificationCommandService;
import com.example.notification.service.setting.NotificationSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final List<String> DEFAULT_CHANNELS = List.of(NotificationChannel.IN_APP.name());

    private final IdempotentConsumerService idempotentConsumerService;
    private final NotificationCommandService notificationCommandService;
    private final ProductItemSummaryClientFacade productClient;
    private final NotificationSettingService notificationSettingService;

    @KafkaListener(topics = "funding-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeFunding(String message) {
        FundingEventMessage event = JsonUtils.fromJson(message, FundingEventMessage.class);
        if (!isValid(event.getEventId(), event.getEventType(), "funding-events")) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "FUNDING_EVENT", () -> {
            switch (normalizeEventType(event.getEventType())) {
                case "FUNDING_SUCCEEDED" -> handleFundingSucceeded(event);
                case "FUNDING_FAILED" -> handleFundingFailed(event);
                default -> log.debug("Ignore funding event type: {}", event.getEventType());
            }
            return null;
        });
    }

    @KafkaListener(topics = "payment-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumePayment(String message) {
        PaymentEventMessage event = JsonUtils.fromJson(message, PaymentEventMessage.class);
        if (!isValid(event.getEventId(), event.getEventType(), "payment-events")) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "PAYMENT_EVENT", () -> {
            if ("PAYMENT_COMPLETED".equals(normalizeEventType(event.getEventType()))) {
                handlePaymentCompleted(event);
            } else {
                log.debug("Ignore payment event type: {}", event.getEventType());
            }
            return null;
        });
    }

    @KafkaListener(topics = "hotdeal-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeHotDeal(String message) {
        HotDealEventMessage event = JsonUtils.fromJson(message, HotDealEventMessage.class);
        if (!isValid(event.getEventId(), event.getEventType(), "hotdeal-events")) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "HOTDEAL_EVENT", () -> {
            if ("HOT_DEAL_STARTED".equals(normalizeEventType(event.getEventType()))) {
                handleHotDealStarted(event);
            } else {
                log.debug("Ignore hotdeal event type: {}", event.getEventType());
            }
            return null;
        });
    }

    @KafkaListener(topics = "stock-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeStock(String message) {
        StockEventMessage event = JsonUtils.fromJson(message, StockEventMessage.class);
        if (!isValid(event.getEventId(), event.getEventType(), "stock-events")) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "STOCK_EVENT", () -> {
            if ("STOCK_DEPLETED".equals(normalizeEventType(event.getEventType()))) {
                handleStockDepleted(event);
            } else {
                log.debug("Ignore stock event type: {}", event.getEventType());
            }
            return null;
        });
    }

    @KafkaListener(topics = "chat-notification-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeChat(String message) {
        ChatNotificationEventMessage event = JsonUtils.fromJson(message, ChatNotificationEventMessage.class);
        if (!isValid(event.getEventId(), event.getEventType(), "chat-notification-events")) {
            return;
        }

        idempotentConsumerService.executeIdempotent(event.getEventId(), "CHAT_NOTIFICATION_EVENT", () -> {
            if ("CHAT_NOTIFICATION_REQUESTED".equals(normalizeEventType(event.getEventType()))) {
                handleChatNotificationRequested(event);
            } else {
                log.debug("Ignore chat notification event type: {}", event.getEventType());
            }
            return null;
        });
    }

    private void handleFundingSucceeded(FundingEventMessage event) {
        if (event.getSellerId() == null) {
            log.warn("Skip FUNDING_SUCCEEDED notification. sellerId is null. campaignId={}", event.getCampaignId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("campaignId", event.getCampaignId());
        variables.put("itemId", event.getItemId());
        variables.put("goalAmount", event.getGoalAmount());
        variables.put("currentAmount", event.getCurrentAmount());
        variables.put("currentQuantity", event.getCurrentQuantity());
        variables.put("fundingType", event.getFundingType());

        dispatchNotification(
                event.getSellerId(),
                NotificationType.FUNDING_SUCCESS,
                "CAMPAIGN",
                event.getCampaignId(),
                event.getEventId(),
                "펀딩이 성공적으로 마감되었어요",
                "캠페인 #" + safeValue(event.getCampaignId()) + "이(가) 목표를 달성했습니다.",
                variables
        );
    }

    private void handleFundingFailed(FundingEventMessage event) {
        if (event.getSellerId() == null) {
            log.warn("Skip FUNDING_FAILED notification. sellerId is null. campaignId={}", event.getCampaignId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("campaignId", event.getCampaignId());
        variables.put("itemId", event.getItemId());
        variables.put("goalAmount", event.getGoalAmount());
        variables.put("currentAmount", event.getCurrentAmount());
        variables.put("currentQuantity", event.getCurrentQuantity());
        variables.put("fundingType", event.getFundingType());

        dispatchNotification(
                event.getSellerId(),
                NotificationType.FUNDING_FAIL,
                "CAMPAIGN",
                event.getCampaignId(),
                event.getEventId(),
                "펀딩이 목표를 달성하지 못했어요",
                "캠페인 #" + safeValue(event.getCampaignId()) + "이(가) 마감되었지만 목표 달성에 실패했습니다.",
                variables
        );
    }

    private void handlePaymentCompleted(PaymentEventMessage event) {
        if (event.getUserId() == null) {
            log.warn("Skip PAYMENT_COMPLETED notification. userId is null. paymentId={}", event.getPaymentId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("paymentId", event.getPaymentId());
        variables.put("purchaseId", event.getPurchaseId());
        variables.put("orderId", event.getOrderId());
        variables.put("itemId", event.getItemId());
        variables.put("totalAmount", event.getTotalAmount());
        variables.put("quantity", event.getQuantity());

        dispatchNotification(
                event.getUserId(),
                NotificationType.PAYMENT,
                "PURCHASE",
                event.getPurchaseId(),
                event.getEventId(),
                "결제가 완료되었습니다",
                "결제 건 #" + safeValue(event.getPaymentId()) + "이(가) 정상 처리되었습니다.",
                variables
        );
    }

    private void handleHotDealStarted(HotDealEventMessage event) {
        ResolvedRecipient resolved = resolveRecipient(event.getItemId(), event.getSellerId(), event.getTitle());
        if (resolved.recipientId() == null) {
            log.warn("Skip HOT_DEAL_STARTED notification. recipient unresolved. itemId={}", event.getItemId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("hotDealId", event.getHotDealId());
        variables.put("itemId", event.getItemId());
        variables.put("title", resolved.title());
        variables.put("discountedPrice", event.getDiscountedPrice());
        variables.put("discountRate", event.getDiscountRate());
        variables.put("maxQuantity", event.getMaxQuantity());

        dispatchNotification(
                resolved.recipientId(),
                NotificationType.HOTDEAL,
                "HOT_DEAL",
                event.getHotDealId(),
                event.getEventId(),
                "핫딜이 시작되었습니다",
                safeValue(resolved.title()) + " 상품의 핫딜이 시작되었습니다.",
                variables
        );
    }

    private void handleStockDepleted(StockEventMessage event) {
        ResolvedRecipient resolved = resolveRecipient(event.getItemId(), event.getSellerId(), event.getTitle());
        if (resolved.recipientId() == null) {
            log.warn("Skip STOCK_DEPLETED notification. recipient unresolved. itemId={}", event.getItemId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("stockItemId", event.getStockItemId());
        variables.put("itemId", event.getItemId());
        variables.put("title", resolved.title());
        variables.put("quantity", event.getQuantity());
        variables.put("remainingQuantity", event.getRemainingQuantity());
        variables.put("totalQuantity", event.getTotalQuantity());

        dispatchNotification(
                resolved.recipientId(),
                NotificationType.STOCK_DEPLETED,
                "ITEM",
                event.getItemId(),
                event.getEventId(),
                "재고가 모두 소진되었습니다",
                safeValue(resolved.title()) + " 상품의 재고가 모두 소진되었습니다.",
                variables
        );
    }

    private void handleChatNotificationRequested(ChatNotificationEventMessage event) {
        if (event.getRecipientId() == null || event.getRoomId() == null) {
            log.warn("Skip CHAT_NOTIFICATION_REQUESTED. recipientId={}, roomId={}",
                    event.getRecipientId(), event.getRoomId());
            return;
        }

        if (!notificationSettingService.shouldSendNotification(
                event.getRecipientId(),
                NotificationType.CHAT_MESSAGE,
                NotificationChannel.IN_APP
        )) {
            log.debug("Skip CHAT_NOTIFICATION_REQUESTED by setting. recipientId={}, roomId={}",
                    event.getRecipientId(), event.getRoomId());
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("roomId", event.getRoomId());
        variables.put("roomType", event.getRoomType());
        variables.put("messageId", event.getMessageId());
        variables.put("senderId", event.getSenderId());
        variables.put("messageType", event.getMessageType());
        variables.put("preview", event.getPreview());

        dispatchNotification(
                event.getRecipientId(),
                NotificationType.CHAT_MESSAGE,
                "CHAT_ROOM",
                event.getRoomId(),
                event.getEventId(),
                "새 채팅 메시지가 도착했어요",
                normalizeText(event.getPreview()) != null
                        ? event.getPreview()
                        : "참여 중인 채팅방에 새 메시지가 도착했습니다.",
                variables
        );
    }

    private void dispatchNotification(Long recipientId,
                                      NotificationType type,
                                      String referenceType,
                                      Object referenceId,
                                      String eventId,
                                      String title,
                                      String message,
                                      Map<String, Object> variables) {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .recipientId(recipientId)
                .type(type.name())
                .channels(DEFAULT_CHANNELS)
                .referenceType(referenceType)
                .referenceId(referenceId == null ? null : String.valueOf(referenceId))
                .externalEventId(eventId)
                .title(title)
                .message(message)
                .variables(variables)
                .build();
        notificationCommandService.createAndSend(request, null);
    }

    private ResolvedRecipient resolveRecipient(Long itemId, Long explicitRecipientId, String explicitTitle) {
        Long recipientId = explicitRecipientId;
        String title = normalizeText(explicitTitle);

        if ((recipientId == null || title == null) && itemId != null) {
            ProductItemSummary itemSummary = productClient.findItemSummary(itemId);
            if (itemSummary != null) {
                if (recipientId == null) {
                    recipientId = itemSummary.sellerId();
                }
                if (title == null) {
                    title = normalizeText(itemSummary.title());
                }
            }
        }

        return new ResolvedRecipient(recipientId, title);
    }

    private boolean isValid(String eventId, String eventType, String topic) {
        if (eventId == null || eventType == null) {
            log.warn("Skip invalid {} message. eventId={}, eventType={}",
                    topic, eventId, eventType);
            return false;
        }
        return true;
    }

    private String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String safeValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private record ResolvedRecipient(Long recipientId, String title) {
    }
}
