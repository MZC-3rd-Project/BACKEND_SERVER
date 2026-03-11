package com.example.store.repository;

import com.example.store.dto.response.StoreDetailResponse;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.entity.Stores;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

public interface StoresRepository extends JpaRepository<Stores, Long> {
    @Query(
                value = """
        SELECT new com.example.store.dto.response.StoreListResponse(
            s.id,
            s.userId,
            s.storeName,
            s.status,
            sp.description,
            sa.address,
            sc.contactValue,
            new com.example.store.dto.image.StoreImageResponse(
                    s.id,
                    si.mediaId,
                    si.imageType,
                    si.sortOrder
                )
        )
        FROM Stores s
        LEFT JOIN StoreProfile sp
            ON sp.store.id = s.id
        LEFT JOIN StoreContact sc
            ON sc.store.id = s.id
            AND sc.isPrimary = true
        LEFT JOIN StoreAddress sa
            ON sa.store.id = s.id
        LEFT JOIN StoreImage si
               ON si.store.id = s.id
              WHERE si.imageType = "THUMBNAIL"
        ORDER BY s.createdAt DESC
       """
    )
    Page<StoreListResponse> findStoreList(Pageable pageable);

    @Query("""
        SELECT new com.example.store.dto.response.StoreDetailResponse(
                s.id,
                s.userId,
                s.storeName,
                s.status,
                sp.description,
                sa.address,
                sa.addressType
            )
            FROM Stores s
                LEFT JOIN StoreProfile sp
                    ON sp.store.id = s.id
                 LEFT JOIN StoreAddress sa
                    ON sa.store.id = s.id
                        AND sa.isDefault = true
                        AND sa.deletedAt IS NULL
            WHERE s.id = :storeId
                AND s.deletedAt is null
    """)
    Optional<StoreDetailResponse> findByStoreId(@Param("storeId") Long storeId);//storeId = id(pk)

    boolean existsByUserIdAndDeletedAtIsNull(Long userId);

    Optional<Stores> findByIdAndDeletedAtIsNull(Long storeId);

}
