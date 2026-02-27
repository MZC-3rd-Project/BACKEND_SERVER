package com.example.notification.service.email;

import com.example.notification.config.NotificationEmailProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class RoutingEmailSender implements EmailSender {

    private final NotificationEmailProperties properties;
    private final List<EmailSenderStrategy> strategies;
    private final Map<EmailProviderType, EmailSenderStrategy> strategyMap = new EnumMap<>(EmailProviderType.class);

    @PostConstruct
    void initializeStrategyMap() {
        for (EmailSenderStrategy strategy : strategies) {
            EmailSenderStrategy previous = strategyMap.put(strategy.providerType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate email strategy for provider: " + strategy.providerType());
            }
        }
    }

    @Override
    public EmailSendResult send(EmailSendCommand command) {
        EmailProviderType primary = properties.getProvider();
        EmailSendResult primaryResult = sendVia(primary, command);
        if (primaryResult.isSuccess() || primaryResult.isSkipped()) {
            return primaryResult;
        }

        if (!properties.isFallbackEnabled()) {
            return primaryResult;
        }

        EmailProviderType fallback = properties.getFallbackProvider();
        if (fallback == null || fallback == primary) {
            return primaryResult;
        }

        EmailSendResult fallbackResult = sendVia(fallback, command);
        if (fallbackResult.isSuccess() || fallbackResult.isSkipped()) {
            return fallbackResult;
        }

        log.warn("Email send failed on both providers. primary={}, fallback={}", primary, fallback);
        return EmailSendResult.failure(
                primary.name(),
                "primary=" + primaryResult.getErrorMessage() + ", fallback=" + fallbackResult.getErrorMessage()
        );
    }

    private EmailSendResult sendVia(EmailProviderType providerType, EmailSendCommand command) {
        EmailSenderStrategy strategy = strategyMap.get(providerType);
        if (strategy == null) {
            return EmailSendResult.failure(providerType.name(), "email strategy not configured");
        }
        return strategy.send(command);
    }
}
