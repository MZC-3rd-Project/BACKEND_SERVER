package com.example.media.repository;

import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    List<MediaFile> findByStatusAndUploadTokenExpiresAtBefore(MediaStatus status, LocalDateTime baseTime, Pageable pageable);
}
