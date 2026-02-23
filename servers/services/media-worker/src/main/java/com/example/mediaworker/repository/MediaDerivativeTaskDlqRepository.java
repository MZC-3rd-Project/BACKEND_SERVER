package com.example.mediaworker.repository;

import com.example.mediaworker.entity.MediaDerivativeTaskDlq;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaDerivativeTaskDlqRepository extends JpaRepository<MediaDerivativeTaskDlq, Long> {
}
