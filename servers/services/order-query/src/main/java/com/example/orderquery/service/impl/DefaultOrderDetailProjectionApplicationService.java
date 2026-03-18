package com.example.orderquery.service.impl;

import com.example.orderquery.service.OrderDetailProjectionApplicationService;
import com.example.orderquery.service.projection.OrderDetailEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultOrderDetailProjectionApplicationService implements OrderDetailProjectionApplicationService {

    private final List<OrderDetailEventHandler> eventHandlers;

    @Override
    public void project(String eventType, String payloadJson) {
        eventHandlers.stream()
                .filter(handler -> handler.supports(eventType))
                .findFirst()
                .ifPresentOrElse(
                        handler -> handler.handle(payloadJson),
                        () -> log.warn("[OrderDetailProjection] no handler for eventType={}", eventType)
                );
    }
}
