package com.example.notification.repository;

import com.example.notification.entity.NotificationUserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationUserPreferenceRepository extends JpaRepository<NotificationUserPreference, Long> {

    Optional<NotificationUserPreference> findByUserId(Long userId);
}
