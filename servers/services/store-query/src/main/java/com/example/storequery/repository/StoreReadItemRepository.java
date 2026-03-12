package com.example.storequery.repository;

import com.example.storequery.entity.StoreReadItem;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreReadItemRepository extends JpaRepository<StoreReadItem, Long> {

    List<StoreReadItem> findByStoreIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(Long storeId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update StoreReadItem item
           set item.deletedAt = CURRENT_TIMESTAMP
         where item.storeId = :storeId
           and item.deletedAt is null
    """)
    void softDeleteActiveByStoreId(@Param("storeId") Long storeId);
}
