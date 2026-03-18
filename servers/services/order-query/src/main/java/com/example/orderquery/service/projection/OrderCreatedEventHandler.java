package com.example.orderquery.service.projection;

import com.example.core.id.Snowflake;
import com.example.orderquery.entity.OrderItem;
import com.example.orderquery.entity.Orders;
import com.example.orderquery.entity.Enums.ItemType;
import com.example.orderquery.entity.Enums.OrderSourceType;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.orderquery.repository.OrderItemRepository;
import com.example.orderquery.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler implements OrderDetailEventHandler {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;
    private final PayloadParser payloadParser;
    private final Snowflake snowflake;

    @Override
    public boolean supports(String eventType) {
        return "ORDER_CREATED_EVENT".equals(eventType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(String payloadJson) {
        Map<String, Object> payload = payloadParser.parse(payloadJson);

        Long orderId = payloadParser.getLong(payload, "orderId");
        Long userId = payloadParser.getLong(payload, "userId");
        Long totalAmount = payloadParser.getLong(payload, "totalAmount");
        String recipientName = payloadParser.getString(payload, "recipientName");
        String recipientPhone = payloadParser.getString(payload, "recipientPhone");
        String deliveryMemo = payloadParser.getString(payload, "deliveryMemo");

        Object expiresAtRaw = payload.get("expiresAt");
        LocalDateTime expiresAt = expiresAtRaw == null ? null
                : LocalDateTime.parse(expiresAtRaw.toString());

        // sourceType: 아이템 channelType 기반으로 결정 (없으면 GENERAL)
        List<Map<String, Object>> itemsPayload = (List<Map<String, Object>>) payload.get("items");
        OrderSourceType sourceType = resolveSourceType(itemsPayload);

        Orders orders = Orders.create(
                orderId, userId, sourceType, OrderStatus.PENDING,
                totalAmount, recipientName, recipientPhone, deliveryMemo, expiresAt
        );
        ordersRepository.save(orders);

        if (itemsPayload != null) {
            for (Map<String, Object> itemMap : itemsPayload) {
                Long itemId = payloadParser.getLong(itemMap, "itemId");
                Long storeId = payloadParser.getLong(itemMap, "storeId");
                String channelType = payloadParser.getString(itemMap, "channelType");
                Long channelRefId = payloadParser.getLong(itemMap, "channelRefId");
                Integer quantity = payloadParser.getInteger(itemMap, "quantity");
                Long unitPrice = payloadParser.getLong(itemMap, "unitPrice");
                Long lineAmount = payloadParser.getLong(itemMap, "lineAmount");
                String titleSnap = payloadParser.getString(itemMap, "titleSnap");
                String itemTypeSnap = payloadParser.getString(itemMap, "itemTypeSnap");

                ItemType itemType = itemTypeSnap != null ? ItemType.valueOf(itemTypeSnap) : ItemType.PRODUCT;

                OrderItem orderItem = OrderItem.create(
                        snowflake.nextId(),
                        orderId, itemId, storeId,
                        channelType, channelRefId,
                        itemType, titleSnap,
                        unitPrice, unitPrice, lineAmount, quantity
                );
                orderItemRepository.save(orderItem);
            }
        }

        log.info("[OrderCreatedEventHandler] order projected. orderId={}", orderId);
    }

    private OrderSourceType resolveSourceType(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return OrderSourceType.GENERAL;
        String channelType = payloadParser.getString(items.get(0), "channelType");
        if ("FUNDING".equals(channelType)) return OrderSourceType.FUNDING;
        if ("HOT_DEAL".equals(channelType)) return OrderSourceType.HOT_DEAL;
        return OrderSourceType.GENERAL;
    }
}
