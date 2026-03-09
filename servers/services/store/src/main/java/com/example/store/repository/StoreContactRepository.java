package com.example.store.repository;

import com.example.store.entity.StoreContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreContactRepository extends JpaRepository<StoreContact, Long> {
    Optional<StoreContact> findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(Long storeId);
}
