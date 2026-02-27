package com.example.notification.service.template;

import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationTemplate;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationTemplateResolver {

    private static final String DEFAULT_LOCALE = "ko-KR";

    private final NotificationTemplateRepository notificationTemplateRepository;

    public Optional<NotificationTemplate> resolve(NotificationType type,
                                                  NotificationChannel channel,
                                                  String requestedLocale) {
        for (String candidate : candidateLocales(requestedLocale)) {
            Optional<NotificationTemplate> template = notificationTemplateRepository
                    .findTopByTypeAndChannelAndLocaleAndEnabledTrueOrderByVersionDesc(type, channel, candidate);
            if (template.isPresent()) {
                return template;
            }
        }
        return Optional.empty();
    }

    private List<String> candidateLocales(String requestedLocale) {
        Set<String> candidates = new LinkedHashSet<>();
        if (requestedLocale != null && !requestedLocale.isBlank()) {
            String normalized = requestedLocale.trim();
            candidates.add(normalized);
            int separator = normalized.indexOf('-');
            if (separator > 0) {
                candidates.add(normalized.substring(0, separator));
            }
        }
        candidates.add(DEFAULT_LOCALE);
        candidates.add("en-US");
        return new ArrayList<>(candidates);
    }
}
