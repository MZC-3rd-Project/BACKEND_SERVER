package com.example.notification.consumer.chat;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.notification.consumer.support.NotificationDispatchSupport;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationType;
import com.example.notification.service.setting.NotificationSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = ChatNotificationEventProcessor.CONSUMER_NAME)
public class ChatNotificationEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "notification-chat-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "CHAT_NOTIFICATION_EVENT";

    private final NotificationDispatchSupport notificationDispatchSupport;
    private final NotificationSettingService notificationSettingService;
    private final Map<String, EventSpec<ChatNotificationEventMessage>> eventSpecs;

    public ChatNotificationEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            NotificationDispatchSupport notificationDispatchSupport,
            NotificationSettingService notificationSettingService
    ) {
        super(idempotentConsumerService);
        this.notificationDispatchSupport = notificationDispatchSupport;
        this.notificationSettingService = notificationSettingService;
        this.eventSpecs = Map.of(
                "CHAT_NOTIFICATION_REQUESTED",
                EventSpec.of(
                        ChatNotificationEventMessage.class,
                        this::hasRequiredPayload,
                        this::handleChatNotificationRequested
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<ChatNotificationEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.warn("Skip invalid chat-notification-events message. eventId={}, eventType={}", eventId, eventType);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        ChatNotificationEventMessage chatEvent = (ChatNotificationEventMessage) event;
        log.warn("Skip CHAT_NOTIFICATION_REQUESTED. recipientId={}, roomId={}",
                chatEvent == null ? null : chatEvent.getRecipientId(),
                chatEvent == null ? null : chatEvent.getRoomId());
    }

    private boolean hasRequiredPayload(ChatNotificationEventMessage event) {
        return event.getRecipientId() != null && event.getRoomId() != null;
    }

    private void handleChatNotificationRequested(ChatNotificationEventMessage event) {
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

        notificationDispatchSupport.dispatchNotification(
                event.getRecipientId(),
                NotificationType.CHAT_MESSAGE,
                "CHAT_ROOM",
                event.getRoomId(),
                event.getEventId(),
                "새 채팅 메시지가 도착했어요",
                notificationDispatchSupport.normalizeText(event.getPreview()) != null
                        ? event.getPreview()
                        : "참여 중인 채팅방에 새 메시지가 도착했습니다.",
                variables
        );
        log.info("Chat notification dispatched. eventId={}, roomId={}, recipientId={}",
                event.getEventId(), event.getRoomId(), event.getRecipientId());
    }
}
