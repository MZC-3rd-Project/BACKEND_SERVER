package com.example.funding.repository;

import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FundingCampaignRepository extends JpaRepository<FundingCampaign, Long> {

    Optional<FundingCampaign> findByItemId(Long itemId);

    boolean existsByItemId(Long itemId);

    @Query("SELECT c FROM FundingCampaign c WHERE c.status = :status AND c.endAt <= :now")
    List<FundingCampaign> findExpiredCampaigns(@Param("status") FundingStatus status,
                                                @Param("now") LocalDateTime now);

    @Query("SELECT c FROM FundingCampaign c WHERE c.status = :status " +
            "AND (:cursor IS NULL OR c.id < :cursor) ORDER BY c.id DESC")
    List<FundingCampaign> findByStatusWithCursor(@Param("status") FundingStatus status,
                                                  @Param("cursor") Long cursor,
                                                  org.springframework.data.domain.Pageable pageable);

    @Query("SELECT c FROM FundingCampaign c " +
            "WHERE (:cursor IS NULL OR c.id < :cursor) ORDER BY c.id DESC")
    List<FundingCampaign> findAllWithCursor(@Param("cursor") Long cursor,
                                             org.springframework.data.domain.Pageable pageable);
}
