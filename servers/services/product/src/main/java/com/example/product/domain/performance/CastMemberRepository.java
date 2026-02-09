package com.example.product.domain.performance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CastMemberRepository extends JpaRepository<CastMember, Long> {

    List<CastMember> findByPerformanceId(Long performanceId);

    void deleteAllByPerformanceId(Long performanceId);
}
