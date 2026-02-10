package com.example.product.repository;

import com.example.product.entity.performance.CastMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CastMemberRepository extends JpaRepository<CastMember, Long> {

    List<CastMember> findByPerformanceId(Long performanceId);

    List<CastMember> findByPerformanceIdIn(List<Long> performanceIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CastMember cm SET cm.deletedAt = CURRENT_TIMESTAMP WHERE cm.performanceId = :performanceId AND cm.deletedAt IS NULL")
    void softDeleteAllByPerformanceId(@Param("performanceId") Long performanceId);
}
