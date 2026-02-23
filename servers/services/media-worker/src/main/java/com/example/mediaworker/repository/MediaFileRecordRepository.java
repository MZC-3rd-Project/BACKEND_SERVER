package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaFileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRecordRepository extends JpaRepository<MediaFileRecord, Long> {
}
