package com.example.orderquery.service;

public interface OrderDetailProjectionApplicationService {

    void project(String eventType, String payloadJson);
}
