package com.example.funding.repository;

import com.example.funding.entity.FundingCampaign;
import com.example.funding.entity.FundingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FundingCampaignRepository extends JpaRepository<FundingCampaign, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FundingCampaign c WHERE c.id = :id")
    Optional<FundingCampaign> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query("UPDATE FundingCampaign c SET c.currentAmount = c.currentAmount + :amount, " +
            "c.currentQuantity = c.currentQuantity + :quantity " +
            "WHERE c.id = :id " +
            "AND c.status = :status " +
            "AND c.startAt <= :now " +
            "AND c.endAt > :now " +
            "AND (c.goalAmount IS NULL OR c.currentAmount + :amount <= c.goalAmount) " +
            "AND (c.goalQuantity IS NULL OR c.currentQuantity + :quantity <= c.goalQuantity)")
    int incrementParticipationIfAvailable(@Param("id") Long id,
                                          @Param("amount") Long amount,
                                          @Param("quantity") int quantity,
                                          @Param("status") FundingStatus status,
                                          @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE FundingCampaign c SET c.currentAmount = c.currentAmount - :amount, " +
            "c.currentQuantity = c.currentQuantity - :quantity WHERE c.id = :id")
    int decrementParticipation(@Param("id") Long id, @Param("amount") Long amount, @Param("quantity") int quantity);

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
