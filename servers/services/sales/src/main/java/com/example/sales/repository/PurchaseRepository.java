package com.example.sales.repository;

import com.example.sales.entity.Purchase;
import com.example.sales.entity.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByOrderId(Long orderId);

    long countByItemIdAndStatusIn(Long itemId, List<PurchaseStatus> statuses);

    @Query("""
            select p.itemId as itemId, max(p.id) as cursorId
            from Purchase p
            where p.status in :statuses
              and (:cursorId is null or p.id < :cursorId)
            group by p.itemId
            order by max(p.id) desc
            """)
    List<SalesItemCursorProjection> findSalesItemCursorPage(
            @Param("statuses") List<PurchaseStatus> statuses,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select count(distinct p.itemId)
            from Purchase p
            where p.status in :statuses
            """)
    long countDistinctItemIdByStatusIn(@Param("statuses") List<PurchaseStatus> statuses);
}
