package com.example.product.consumer.funding;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemStatusHistory;
import com.example.product.event.ItemStatusChangedEvent;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemStatusHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = FundingEventProcessor.CONSUMER_NAME)
public class FundingEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "product-funding-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "FUNDING_EVENT";
    private final ItemRepository itemRepository;
    private final ItemStatusHistoryRepository statusHistoryRepository;
    private final EventPublisher eventPublisher;
    private final Map<String, EventSpec<FundingEventMessage>> eventSpecs;

    public FundingEventProcessor(
            ItemRepository itemRepository,
            ItemStatusHistoryRepository statusHistoryRepository,
            EventPublisher eventPublisher,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.itemRepository = itemRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.eventSpecs = Map.of(
                "FUNDING_SUCCEEDED", EventSpec.of(FundingEventMessage.class, this::hasItemId, this::handleFundingSucceeded),
                "FUNDING_FAILED", EventSpec.of(FundingEventMessage.class, this::hasItemId, this::handleFundingFailed)
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected void onInvalidEnvelope(String eventId, String eventType, String message) {
        log.error("[FundingEventProcessor] eventId 또는 eventType이 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.error("[FundingEventProcessor] itemId가 null입니다. message={}", message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[FundingEventProcessor] 이벤트 처리 실패. message={}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, EventSpec<FundingEventMessage>> eventSpecs() {
        return eventSpecs;
    }

    private boolean hasItemId(FundingEventMessage event) {
        return event.getItemId() != null;
    }

    private void handleFundingSucceeded(FundingEventMessage event) {
        changeItemStatus(event.getItemId(), ItemStatus.FUNDED, "펀딩 성공");
    }

    private void handleFundingFailed(FundingEventMessage event) {
        changeItemStatus(event.getItemId(), ItemStatus.FUND_FAILED, "펀딩 실패");
    }

    private void changeItemStatus(Long itemId, ItemStatus newStatus, String reason) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.ITEM_NOT_FOUND));

        ItemStatus previousStatus = item.getStatus();
        item.changeStatus(newStatus);

        statusHistoryRepository.save(
                ItemStatusHistory.create(itemId, previousStatus, newStatus, reason, null));

        eventPublisher.publish(
                new ItemStatusChangedEvent(
                        itemId,
                        previousStatus.name(),
                        newStatus.name(),
                        item.getItemType().name(),
                        item.getSellerId(),
                        item.getStoreId()
                ),
                EventMetadata.of("Item", String.valueOf(itemId)));
    }
}
