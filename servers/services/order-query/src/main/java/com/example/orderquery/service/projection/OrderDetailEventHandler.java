package com.example.orderquery.service.projection;

public interface OrderDetailEventHandler {

    boolean supports(String eventType);

    void handle(String payloadJson);
}
