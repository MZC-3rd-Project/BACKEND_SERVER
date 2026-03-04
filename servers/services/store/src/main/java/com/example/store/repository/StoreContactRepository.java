package com.example.store.repository;

import com.example.store.entity.StoreContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreContactRepository extends JpaRepository<StoreContact, Long> {
}
