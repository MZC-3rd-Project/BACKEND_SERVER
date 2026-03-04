package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaDerivativeRepository extends JpaRepository<MediaDerivative, Long> {

    Optional<MediaDerivative> findByMediaIdAndDerivativeProfileAndMediaVersion(
            Long mediaId,
            MediaDerivativeProfile derivativeProfile,
            Long mediaVersion
    );

    Optional<MediaDerivative> findTopByMediaIdAndDerivativeProfileAndStatusOrderByMediaVersionDescCreatedAtDesc(
            Long mediaId,
            MediaDerivativeProfile derivativeProfile,
            MediaDerivativeStatus status
    );

    @Query("""
            select derivative
            from MediaDerivative derivative
            where derivative.status = :status
              and not exists (
                  select 1
                  from MediaFileRecord mediaFile
                  where mediaFile.id = derivative.mediaId
              )
            order by derivative.id asc
            """)
    List<MediaDerivative> findOrphanedDerivatives(
            @Param("status") MediaDerivativeStatus status,
            Pageable pageable
    );
}
