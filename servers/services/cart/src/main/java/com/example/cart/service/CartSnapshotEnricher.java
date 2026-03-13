package com.example.cart.service;

public interface CartSnapshotEnricher {

    CartSnapshotData enrich(Long itemId);
}
