package com.example.product.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {

    List<SeatGrade> findByPerformanceIdOrderByPriceDesc(Long performanceId);

    void deleteAllByPerformanceId(Long performanceId);
}
