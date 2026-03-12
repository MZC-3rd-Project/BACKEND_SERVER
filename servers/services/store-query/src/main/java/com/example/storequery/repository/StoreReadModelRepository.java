package com.example.storequery.repository;

import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.entity.StoreQueryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreReadModelRepository extends JpaRepository<StoreReadModel, Long> {

    Page<StoreReadModel> findByDeletedAtIsNullOrderBySourceUpdatedAtDesc(Pageable pageable);

    Page<StoreReadModel> findByDeletedAtIsNullAndStatusOrderBySourceUpdatedAtDesc(
        StoreQueryStatus status,
        Pageable pageable
    );

    Optional<StoreReadModel> findByStoreIdAndDeletedAtIsNull(Long storeId);

    List<StoreReadModel> findByUserIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(Long userId);

    @Query(
        value = """
            SELECT *
              FROM store_read_models s
             WHERE s.deleted_at IS NULL
               AND (:status IS NULL OR s.status = :status)
               AND (
                    s.search_tsv @@ plainto_tsquery('simple', :keyword)
                 OR s.store_name ILIKE CONCAT('%', :keyword, '%')
                 OR COALESCE(s.owner_nickname, '') ILIKE CONCAT('%', :keyword, '%')
                 OR COALESCE(s.search_text, '') ILIKE CONCAT('%', :keyword, '%')
                 OR similarity(s.store_name, :keyword) >= :threshold
                 OR similarity(COALESCE(s.owner_nickname, ''), :keyword) >= :threshold
                 OR similarity(COALESCE(s.search_text, ''), :keyword) >= :threshold
               )
             ORDER BY GREATEST(
                    ts_rank_cd(s.search_tsv, plainto_tsquery('simple', :keyword)),
                    similarity(s.store_name, :keyword),
                    similarity(COALESCE(s.owner_nickname, ''), :keyword),
                    similarity(COALESCE(s.search_text, ''), :keyword)
               ) DESC,
               s.source_updated_at DESC
            """,
        countQuery = """
            SELECT COUNT(*)
              FROM store_read_models s
             WHERE s.deleted_at IS NULL
               AND (:status IS NULL OR s.status = :status)
               AND (
                    s.search_tsv @@ plainto_tsquery('simple', :keyword)
                 OR s.store_name ILIKE CONCAT('%', :keyword, '%')
                 OR COALESCE(s.owner_nickname, '') ILIKE CONCAT('%', :keyword, '%')
                 OR COALESCE(s.search_text, '') ILIKE CONCAT('%', :keyword, '%')
                 OR similarity(s.store_name, :keyword) >= :threshold
                 OR similarity(COALESCE(s.owner_nickname, ''), :keyword) >= :threshold
                 OR similarity(COALESCE(s.search_text, ''), :keyword) >= :threshold
               )
            """,
        nativeQuery = true
    )
    Page<StoreReadModel> search(
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("threshold") double threshold,
        Pageable pageable
    );
}
