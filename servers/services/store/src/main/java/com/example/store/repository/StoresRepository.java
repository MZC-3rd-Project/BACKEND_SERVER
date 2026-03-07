package com.example.store.repository;

import com.example.store.dto.response.StoreListResponse;
import com.example.store.entity.Stores;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.domain.Pageable;

public interface StoresRepository extends JpaRepository<Stores, Long> {
    @Query(
                value = """
        SELECT new com.example.store.dto.response.StoreListResponse(
            s.userId,
            s.storeName,
            s.status,
            sp.description,
            sa.address,
            sc.contactValue
        )
        FROM Stores s
        LEFT JOIN StoreProfile sp
            ON sp.store.id = s.id
        LEFT JOIN StoreContact sc
            ON sc.store.id = s.id
            AND sc.isPrimary = true
        LEFT JOIN StoreAddress sa
            ON sa.store.id = s.id
        ORDER BY s.createdAt DESC
        """
    )
    Page<StoreListResponse> findStoreList(Pageable pageable);

}
