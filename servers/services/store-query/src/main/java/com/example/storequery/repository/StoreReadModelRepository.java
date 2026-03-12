package com.example.storequery.repository;

import com.example.storequery.entity.StoreReadModel;
import com.example.storequery.entity.StoreQueryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StoreReadModelRepository extends JpaRepository<StoreReadModel, Long> {

    @Query("""
        select s
          from StoreReadModel s
         where s.deletedAt is null
           and (:status is null or s.status = :status)
           and (
                :cursorUpdatedAt is null
                or s.sourceUpdatedAt < :cursorUpdatedAt
                or (s.sourceUpdatedAt = :cursorUpdatedAt and s.storeId < :cursorStoreId)
           )
         order by s.sourceUpdatedAt desc, s.storeId desc
        """)
    List<StoreReadModel> findListWithCursor(
        @Param("status") StoreQueryStatus status,
        @Param("cursorUpdatedAt") LocalDateTime cursorUpdatedAt,
        @Param("cursorStoreId") Long cursorStoreId,
        Pageable pageable
    );

    Optional<StoreReadModel> findByStoreIdAndDeletedAtIsNull(Long storeId);

    List<StoreReadModel> findByUserIdAndDeletedAtIsNullOrderBySourceUpdatedAtDesc(Long userId);

    @Query(
        value = """
            SELECT ranked.store_id           AS storeId,
                   ranked.user_id            AS userId,
                   ranked.store_name         AS storeName,
                   ranked.status             AS status,
                   ranked.description        AS description,
                   ranked.primary_contact_value AS primaryContactValue,
                   ranked.default_address    AS defaultAddress,
                   ranked.owner_nickname     AS ownerNickname,
                   ranked.thumbnail_media_id AS thumbnailMediaId,
                   ranked.thumbnail_url      AS thumbnailUrl,
                   ranked.thumbnail_sort_order AS thumbnailSortOrder,
                   ranked.source_updated_at  AS sourceUpdatedAt,
                   ranked.sort_rank          AS sortRank
              FROM (
                    SELECT s.*,
                           GREATEST(
                                ts_rank_cd(s.search_tsv, plainto_tsquery('simple', :keyword)),
                                similarity(s.store_name, :keyword),
                                similarity(COALESCE(s.owner_nickname, ''), :keyword),
                                similarity(COALESCE(s.search_text, ''), :keyword)
                           ) AS sort_rank
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
               ) ranked
             WHERE (
                    :cursorRank IS NULL
                 OR ranked.sort_rank < :cursorRank
                 OR (
                        ranked.sort_rank = :cursorRank
                    AND (
                            ranked.source_updated_at < :cursorUpdatedAt
                         OR (ranked.source_updated_at = :cursorUpdatedAt AND ranked.store_id < :cursorStoreId)
                    )
                 )
               )
             ORDER BY ranked.sort_rank DESC,
                      ranked.source_updated_at DESC,
                      ranked.store_id DESC
             LIMIT :limit
            """,
        nativeQuery = true
    )
    List<StoreReadModelSearchRow> searchWithCursor(
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("threshold") double threshold,
        @Param("cursorRank") Double cursorRank,
        @Param("cursorUpdatedAt") LocalDateTime cursorUpdatedAt,
        @Param("cursorStoreId") Long cursorStoreId,
        @Param("limit") int limit
    );
}
