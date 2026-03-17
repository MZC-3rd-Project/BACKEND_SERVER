package com.example.orderquery.service.projection;

import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemUpdatedEventHandler implements OrderDetailEventHandler {

    private final OrderItemRepository orderItemRepository;
    private final PayloadParser payloadParser;

    @Value("${app.service.media-url}")
    private String mediaBaseUrl;

    @Override
    public boolean supports(String eventType) {
        return "ITEM_UPDATED".equals(eventType);
    }

    @Override
    public void handle(String payloadJson) {
        Map<String, Object> payload = payloadParser.parse(payloadJson);
        Long itemId = payloadParser.getLong(payload, "itemId");
        Long thumbnailMediaId = payloadParser.getLong(payload, "thumbnailMediaId");

        if (itemId == null) {
            log.warn("[ItemUpdatedEventHandler] itemId is null");
            return;
        }

        String thumbnailUrl = thumbnailMediaId != null
                ? mediaBaseUrl + "/media/" + thumbnailMediaId
                : null;

        List<OrderItem> orderItems = orderItemRepository.findByItemId(itemId);
        for (OrderItem orderItem : orderItems) {
            if (thumbnailUrl != null) {
                orderItem.updateThumbnailUrl(thumbnailUrl);
                orderItemRepository.save(orderItem);
            }
        }

        log.info("[ItemUpdatedEventHandler] thumbnail updated. itemId={}, affectedItems={}", itemId, orderItems.size());
    }
}
