package com.example.store.repository;

import com.example.store.entity.StoreImage;
import com.example.store.entity.Stores;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreImageRepository extends JpaRepository<StoreImage, Long> {
    Long store(Stores store);
    Optional<StoreImage> findByStoreId(Long storeId);
    List<StoreImage> findAllByStoreIdAndDeletedAtIsNull(Long storeId);
}
