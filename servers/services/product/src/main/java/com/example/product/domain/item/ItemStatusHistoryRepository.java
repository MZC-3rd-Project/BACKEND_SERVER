package com.example.product.domain.item;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemStatusHistoryRepository extends JpaRepository<ItemStatusHistory, Long> {

    List<ItemStatusHistory> findByItemIdOrderByCreatedAtDesc(Long itemId);
}
