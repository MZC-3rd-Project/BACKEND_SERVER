package com.example.media.repository;

import com.example.media.entity.MediaDerivative;
import com.example.media.entity.MediaDerivativeProfile;
import com.example.media.entity.MediaDerivativeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaDerivativeRepository extends JpaRepository<MediaDerivative, Long> {

    List<MediaDerivative> findByMediaIdInAndDerivativeProfileAndStatusOrderByMediaIdAscMediaVersionDescCreatedAtDesc(
            List<Long> mediaIds,
            MediaDerivativeProfile derivativeProfile,
            MediaDerivativeStatus status
    );
}
