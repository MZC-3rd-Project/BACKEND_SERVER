package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaDerivativeTaskDlq;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaDerivativeTaskDlqRepository extends JpaRepository<MediaDerivativeTaskDlq, Long> {

    List<MediaDerivativeTaskDlq> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
