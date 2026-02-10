package com.example.product.repository;

import com.example.product.entity.performance.SeatGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {

    List<SeatGrade> findByPerformanceIdOrderByPriceDesc(Long performanceId);

    List<SeatGrade> findByPerformanceIdIn(List<Long> performanceIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SeatGrade sg SET sg.deletedAt = CURRENT_TIMESTAMP WHERE sg.performanceId = :performanceId AND sg.deletedAt IS NULL")
    void softDeleteAllByPerformanceId(@Param("performanceId") Long performanceId);
}
