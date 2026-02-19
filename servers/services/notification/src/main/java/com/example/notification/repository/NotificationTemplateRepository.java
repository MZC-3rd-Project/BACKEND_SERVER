package com.example.notification.repository;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationTemplate;
import com.example.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findTopByTypeAndChannelAndLocaleAndEnabledTrueOrderByVersionDesc(
            NotificationType type, NotificationChannel channel, String locale);

    List<NotificationTemplate> findByTypeAndLocaleOrderByVersionDesc(NotificationType type, String locale);
}
