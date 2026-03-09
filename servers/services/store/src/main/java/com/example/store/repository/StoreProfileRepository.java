package com.example.store.repository;

import com.example.store.entity.StoreProfile;
import com.example.store.entity.Stores;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {
    Optional<StoreProfile> findByStoreId(Long storeId);

    Long store(Stores store);
}
