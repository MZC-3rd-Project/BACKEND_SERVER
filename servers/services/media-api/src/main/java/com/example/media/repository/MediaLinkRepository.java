package com.example.media.repository;

import com.example.media.entity.MediaLink;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaLinkRepository extends JpaRepository<MediaLink, Long> {

    Optional<MediaLink> findByMediaIdAndOwnerTypeAndOwnerIdAndUsageType(
            Long mediaId,
            MediaOwnerType ownerType,
            Long ownerId,
            MediaUsageType usageType
    );

    Optional<MediaLink> findByOwnerTypeAndOwnerIdAndMediaId(
            MediaOwnerType ownerType,
            Long ownerId,
            Long mediaId
    );

    List<MediaLink> findByOwnerTypeAndOwnerIdAndUsageTypeOrderBySortOrderAscCreatedAtAsc(
            MediaOwnerType ownerType,
            Long ownerId,
            MediaUsageType usageType
    );

    List<MediaLink> findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(
            MediaOwnerType ownerType,
            Long ownerId
    );

    Optional<MediaLink> findTopByMediaIdOrderByCreatedAtDesc(Long mediaId);

    List<MediaLink> findByMediaIdInOrderByMediaIdAscCreatedAtDesc(List<Long> mediaIds);
}
