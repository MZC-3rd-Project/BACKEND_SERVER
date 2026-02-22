package com.example.product.repository;

import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :itemId")
    Optional<Item> findByIdForUpdate(@Param("itemId") Long itemId);

    List<Item> findByItemTypeOrderByIdDesc(ItemType itemType, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.itemType = :type AND i.id < :cursor ORDER BY i.id DESC")
    List<Item> findByItemTypeAndIdLessThan(@Param("type") ItemType type, @Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.itemType = :type AND i.status IN :statuses ORDER BY i.id DESC")
    List<Item> findByItemTypeAndStatusIn(@Param("type") ItemType type,
                                          @Param("statuses") List<ItemStatus> statuses, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.itemType = :type AND i.status IN :statuses AND i.id < :cursor ORDER BY i.id DESC")
    List<Item> findByItemTypeAndStatusInAndIdLessThan(@Param("type") ItemType type,
                                                       @Param("statuses") List<ItemStatus> statuses,
                                                       @Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.id < :cursor ORDER BY i.id DESC")
    List<Item> findByIdLessThan(@Param("cursor") Long cursor, Pageable pageable);

    List<Item> findBySellerIdOrderByIdDesc(Long sellerId, Pageable pageable);

    List<Item> findByCategoryIdOrderByIdDesc(Long categoryId, Pageable pageable);

    List<Item> findByStatusIn(List<ItemStatus> statuses, Pageable pageable);
}
