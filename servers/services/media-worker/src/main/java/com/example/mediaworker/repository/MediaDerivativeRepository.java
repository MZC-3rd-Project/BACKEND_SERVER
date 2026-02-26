package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaDerivativeRepository extends JpaRepository<MediaDerivative, Long> {

    Optional<MediaDerivative> findByMediaIdAndDerivativeProfileAndMediaVersion(
            Long mediaId,
            MediaDerivativeProfile derivativeProfile,
            Long mediaVersion
    );
}
