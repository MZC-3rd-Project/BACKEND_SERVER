package com.example.orderquery.service.projection;

import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreUpdatedEventHandler implements OrderDetailEventHandler {

    private final OrderItemRepository orderItemRepository;
    private final PayloadParser payloadParser;

    @Override
    public boolean supports(String eventType) {
        return "StoreUpdated".equals(eventType);
    }

    @Override
    public void handle(String payloadJson) {
        Map<String, Object> payload = payloadParser.parse(payloadJson);
        Long storeId = payloadParser.getLong(payload, "storeId");
        String storeName = payloadParser.getString(payload, "storeName");

        if (storeId == null || storeName == null) {
            log.warn("[StoreUpdatedEventHandler] storeId or storeName is null");
            return;
        }

        List<OrderItem> orderItems = orderItemRepository.findByStoreId(storeId);
        for (OrderItem orderItem : orderItems) {
            orderItem.updateStoreNameSnap(storeName);
            orderItemRepository.save(orderItem);
        }

        log.info("[StoreUpdatedEventHandler] storeName updated. storeId={}, affectedItems={}", storeId, orderItems.size());
    }
}
