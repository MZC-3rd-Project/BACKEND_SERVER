package com.example.store.repository;

import com.example.store.entity.Stores;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoresRepository extends JpaRepository<Stores, Long> {
}
