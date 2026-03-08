package com.example.stock.consumer.item;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.stock.dto.request.InitializeStockRequest;
import com.example.stock.entity.StockItemType;
import com.example.stock.service.command.StockCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = StockItemEventProcessor.CONSUMER_NAME)
public class StockItemEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "stock-item-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "ITEM_EVENT";
    private final StockCommandService stockCommandService;
    private final Map<String, EventSpec<ItemEventMessage>> eventSpecs;

    public StockItemEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            StockCommandService stockCommandService
    ) {
        super(idempotentConsumerService);
        this.stockCommandService = stockCommandService;
        this.eventSpecs = Map.of(
                "ITEM_CREATED", EventSpec.of(ItemEventMessage.class, this::hasItemId, this::handleItemCreated)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[ItemConsumer] eventId/eventType/itemId 중 null 값이 존재합니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[ItemConsumer] eventId/eventType/itemId 중 null 값이 존재합니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[ItemConsumer] 이벤트 처리 실패: {}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected void onUnsupportedEventType(String eventId, String eventType) {
        log.debug("처리하지 않는 이벤트 타입: {}", eventType);
    }

    @Override
    protected Map<String, EventSpec<ItemEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasItemId(ItemEventMessage event) {
        return event.getItemId() != null;
    }

    private void handleItemCreated(ItemEventMessage event) {
        log.info("[ItemConsumer] 아이템 생성 이벤트 수신: itemId={}, type={}", event.getItemId(), event.getItemType());

        List<ItemEventMessage.StockItemPayload> stockItems = event.getStockItems();
        if (stockItems == null || stockItems.isEmpty()) {
            log.info("[ItemConsumer] stockItems 없음 — 재고 초기화 건너뜀: itemId={}", event.getItemId());
            return;
        }

        for (ItemEventMessage.StockItemPayload stockItem : stockItems) {
            try {
                if (!isValidStockPayload(event.getItemId(), stockItem)) {
                    continue;
                }

                StockItemType stockItemType = parseStockItemType(
                        stockItem.getType(),
                        event.getItemId(),
                        stockItem.getReferenceId()
                );
                if (stockItemType == null) {
                    continue;
                }

                InitializeStockRequest request = InitializeStockRequest.of(
                        event.getItemId(),
                        stockItemType,
                        stockItem.getReferenceId(),
                        stockItem.getTotalQuantity()
                );
                stockCommandService.initializeStock(request);
                log.info("[ItemConsumer] 재고 자동 초기화 완료: itemId={}, type={}, refId={}, qty={}",
                        event.getItemId(),
                        stockItem.getType(),
                        stockItem.getReferenceId(),
                        stockItem.getTotalQuantity());
            } catch (Exception exception) {
                log.error("[ItemConsumer] 재고 초기화 실패(메시지 재처리를 위해 예외 전파): itemId={}, type={}, refId={}",
                        event.getItemId(), stockItem.getType(), stockItem.getReferenceId(), exception);
                throw exception;
            }
        }
    }

    private static boolean isValidStockPayload(Long itemId, ItemEventMessage.StockItemPayload payload) {
        if (payload == null) {
            log.error("[ItemConsumer] stockItem payload가 null입니다. itemId={}", itemId);
            return false;
        }
        if (!StringUtils.hasText(payload.getType())) {
            log.error("[ItemConsumer] stockItem.type 누락. itemId={}, refId={}", itemId, payload.getReferenceId());
            return false;
        }
        if (payload.getTotalQuantity() < 0) {
            log.error("[ItemConsumer] stockItem.totalQuantity 음수. itemId={}, type={}, qty={}",
                    itemId, payload.getType(), payload.getTotalQuantity());
            return false;
        }
        return true;
    }

    private static StockItemType parseStockItemType(String rawType, Long itemId, Long referenceId) {
        try {
            return StockItemType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.error("[ItemConsumer] 지원하지 않는 stockItemType. itemId={}, type={}, refId={}",
                    itemId, rawType, referenceId);
            return null;
        }
    }
}
