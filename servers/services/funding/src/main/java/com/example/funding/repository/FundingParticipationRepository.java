package com.example.funding.repository;

import com.example.funding.entity.FundingParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FundingParticipationRepository extends JpaRepository<FundingParticipation, Long> {

    List<FundingParticipation> findByCampaignId(Long campaignId);

    List<FundingParticipation> findByOrderId(Long orderId);

    Optional<FundingParticipation> findByOrderIdAndCampaignId(Long orderId, Long campaignId);

    @Query("SELECT p FROM FundingParticipation p WHERE p.userId = :userId " +
            "AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    List<FundingParticipation> findByUserIdWithCursor(@Param("userId") Long userId,
                                                       @Param("cursor") Long cursor,
                                                       org.springframework.data.domain.Pageable pageable);
}
