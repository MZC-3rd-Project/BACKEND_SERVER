package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.entity.MediaFileStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MediaFileRecordRepository extends JpaRepository<MediaFileRecord, Long> {

    @Query("""
            select record
            from MediaFileRecord record
            where record.status in :statuses
              and (:fromMediaId is null or record.id >= :fromMediaId)
              and (:toMediaId is null or record.id <= :toMediaId)
            order by record.id asc
            """)
    List<MediaFileRecord> findBackfillCandidates(
            @Param("statuses") List<MediaFileStatus> statuses,
            @Param("fromMediaId") Long fromMediaId,
            @Param("toMediaId") Long toMediaId,
            Pageable pageable
    );
}
