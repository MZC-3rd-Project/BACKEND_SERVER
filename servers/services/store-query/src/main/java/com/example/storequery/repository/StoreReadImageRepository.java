package com.example.storequery.repository;

import com.example.storequery.entity.StoreQueryImageType;
import com.example.storequery.entity.StoreReadImage;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoreReadImageRepository extends JpaRepository<StoreReadImage, Long> {

    List<StoreReadImage> findByStoreIdAndDeletedAtIsNullOrderBySortOrderAsc(Long storeId);

    List<StoreReadImage> findByStoreIdAndImageTypeAndDeletedAtIsNullOrderBySortOrderAsc(
        Long storeId,
        StoreQueryImageType imageType
    );

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update StoreReadImage image
           set image.deletedAt = CURRENT_TIMESTAMP
         where image.storeId = :storeId
           and image.deletedAt is null
    """)
    void softDeleteActiveByStoreId(@Param("storeId") Long storeId);
}
