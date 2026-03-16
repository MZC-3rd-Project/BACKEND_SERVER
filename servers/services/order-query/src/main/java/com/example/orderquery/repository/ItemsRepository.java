package com.example.orderquery.repository;

import com.example.orderquery.entity.Enums.ItemStatus;
import com.example.orderquery.entity.Enums.ItemType;
import com.example.orderquery.entity.Items;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemsRepository extends JpaRepository<Items, Long> {

    Page<Items> findAllByStatus(ItemStatus status, Pageable pageable);

    Page<Items> findAllByItemType(ItemType itemType, Pageable pageable);

    Page<Items> findAllByItemTypeAndStatus(ItemType itemType, ItemStatus status, Pageable pageable);
}
