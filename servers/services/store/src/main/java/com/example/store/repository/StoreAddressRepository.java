package com.example.store.repository;

import com.example.store.entity.StoreAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, Long> {
    Optional<StoreAddress> findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(Long storeId);
}
