package com.example.hotdeal.repository;

import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HotDealRepository extends JpaRepository<HotDeal, Long> {

    List<HotDeal> findByStatus(HotDealStatus status);

    Optional<HotDeal> findByItemIdAndStatusIn(Long itemId, List<HotDealStatus> statuses);

    @Query("SELECT h FROM HotDeal h WHERE h.status = :status AND h.id < :cursor ORDER BY h.id DESC")
    List<HotDeal> findByStatusWithCursor(@Param("status") HotDealStatus status,
                                          @Param("cursor") Long cursor,
                                          org.springframework.data.domain.Pageable pageable);

    @Query("SELECT h FROM HotDeal h WHERE h.status = 'ACTIVE' AND h.endAt < :now")
    List<HotDeal> findExpiredActiveDeals(@Param("now") LocalDateTime now);

    boolean existsByItemIdAndStatusIn(Long itemId, List<HotDealStatus> statuses);

    @Modifying
    @Query("UPDATE HotDeal h SET h.soldQuantity = h.soldQuantity + :quantity WHERE h.id = :id")
    int incrementSoldQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
