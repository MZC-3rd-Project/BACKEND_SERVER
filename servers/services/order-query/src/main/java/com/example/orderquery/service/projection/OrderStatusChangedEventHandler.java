package com.example.orderquery.service.projection;

import com.example.orderquery.entity.Orders;
import com.example.orderquery.entity.Enums.OrderStatus;
import com.example.orderquery.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChangedEventHandler implements OrderDetailEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            "ORDER_PAID_EVENT",
            "ORDER_CANCELLED_EVENT",
            "ORDER_REFUND_REQUESTED_EVENT",
            "ORDER_REFUNDED_EVENT"
    );

    private static final Map<String, OrderStatus> EVENT_TO_STATUS = Map.of(
            "ORDER_PAID_EVENT",             OrderStatus.PAID,
            "ORDER_CANCELLED_EVENT",        OrderStatus.CANCELLED,
            "ORDER_REFUND_REQUESTED_EVENT", OrderStatus.REFUND_REQUESTED,
            "ORDER_REFUNDED_EVENT",         OrderStatus.REFUNDED
    );

    private final OrdersRepository ordersRepository;
    private final PayloadParser payloadParser;

    private final ThreadLocal<String> currentEventType = new ThreadLocal<>();

    @Override
    public boolean supports(String eventType) {
        if (SUPPORTED.contains(eventType)) {
            currentEventType.set(eventType);
            return true;
        }
        return false;
    }

    @Override
    public void handle(String payloadJson) {
        String eventType = currentEventType.get();
        currentEventType.remove();

        Map<String, Object> payload = payloadParser.parse(payloadJson);
        Long orderId = payloadParser.getLong(payload, "orderId");

        OrderStatus newStatus = EVENT_TO_STATUS.get(eventType);
        if (newStatus == null) {
            log.warn("[OrderStatusChangedEventHandler] unknown event type: {}", eventType);
            return;
        }

        ordersRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    order.updateStatus(newStatus);
                    ordersRepository.save(order);
                    log.info("[OrderStatusChangedEventHandler] status updated. orderId={}, status={}", orderId, newStatus);
                },
                () -> log.warn("[OrderStatusChangedEventHandler] order not found. orderId={}", orderId)
        );
    }
}
