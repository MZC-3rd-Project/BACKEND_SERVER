package com.example.storequery.repository;

import com.example.storequery.entity.StoreRebuildJob;
import com.example.storequery.entity.StoreRebuildJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRebuildJobRepository extends JpaRepository<StoreRebuildJob, Long> {

    List<StoreRebuildJob> findByStatusOrderByCreatedAtDesc(StoreRebuildJobStatus status);
}
