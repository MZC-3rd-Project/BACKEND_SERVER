package com.example.store.repository;

import com.example.store.entity.StoreProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {
}
