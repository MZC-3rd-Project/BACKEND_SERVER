package com.example.hotdeal.consumer.stock;

import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.HotDealCommandService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = StockEventProcessor.CONSUMER_NAME)
public class StockEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "hotdeal-stock-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "STOCK_EVENT";
    private static final String STOCK_THRESHOLD_REACHED = "STOCK_THRESHOLD_REACHED";

    private final HotDealRepository hotDealRepository;
    private final HotDealCommandService hotDealCommandService;
    private final ProductItemQueryClientFacade productClient;
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public StockEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            HotDealRepository hotDealRepository,
            HotDealCommandService hotDealCommandService,
            ProductItemQueryClientFacade productClient
    ) {
        super(idempotentConsumerService);
        this.hotDealRepository = hotDealRepository;
        this.hotDealCommandService = hotDealCommandService;
        this.productClient = productClient;
        this.eventSpecs = Map.of(
                STOCK_THRESHOLD_REACHED,
                EventSpec.of(
                        StockThresholdReachedEventMessage.class,
                        this::hasRequiredPayload,
                        this::handleStockThresholdReached
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    @Override
    protected <T extends EventEnvelope> void onInvalidPayload(T event, String message, String eventId, String eventType) {
        log.warn("[HotDealStockEventProcessor] invalid payload. eventId={}, eventType={}, message={}",
                eventId, eventType, message);
    }

    @Override
    protected <T extends EventEnvelope> void onProcessingException(
            T event,
            String message,
            String eventId,
            String eventType,
            Exception exception
    ) {
        log.error("[HotDealStockEventProcessor] event consume failed. message={}", message, exception);
        throw propagate(exception);
    }

    private boolean hasRequiredPayload(StockThresholdReachedEventMessage event) {
        return event != null
                && event.getItemId() != null
                && event.getTotalQuantity() != null
                && event.getRemainingQuantity() != null;
    }

    private void handleStockThresholdReached(StockThresholdReachedEventMessage event) {
        if (event.getTotalQuantity() == 0) {
            return;
        }

        boolean exists = hotDealRepository.existsByItemIdAndStatusIn(
                event.getItemId(), List.of(HotDealStatus.SCHEDULED, HotDealStatus.ACTIVE));
        if (exists) {
            return;
        }

        double remainingRate = (double) event.getRemainingQuantity() / event.getTotalQuantity() * 100;
        if (remainingRate < 20) {
            return;
        }

        JsonNode itemData = productClient.findItem(event.getItemId());
        String title = itemData.path("title").asText();
        long price = itemData.path("price").asLong();

        hotDealCommandService.createAndActivate(
                event.getItemId(),
                title,
                price,
                resolveDiscountRate(remainingRate),
                event.getRemainingQuantity(),
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                "재고 임계값 이벤트 - 잔여율 " + String.format("%.1f", remainingRate) + "%"
        );

        log.info("[HotDealStockEventProcessor] hot deal created. itemId={}, remainingRate={}%",
                event.getItemId(), String.format("%.1f", remainingRate));
    }

    private int resolveDiscountRate(double remainingRate) {
        if (remainingRate >= 50) {
            return 10;
        }
        if (remainingRate >= 30) {
            return 20;
        }
        return 30;
    }
}
