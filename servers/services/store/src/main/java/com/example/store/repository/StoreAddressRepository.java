package com.example.store.repository;

import com.example.store.entity.StoreAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, Long> {
}
