package com.example.stock.repository;

import com.example.stock.entity.StockSyncVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockSyncVersionRepository extends JpaRepository<StockSyncVersion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockSyncVersion s WHERE s.itemId = :itemId")
    Optional<StockSyncVersion> findByItemIdWithLock(@Param("itemId") Long itemId);
}
