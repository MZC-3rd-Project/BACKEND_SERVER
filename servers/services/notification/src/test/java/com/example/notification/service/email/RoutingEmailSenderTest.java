package com.example.notification.service.email;

import com.example.notification.config.NotificationEmailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingEmailSenderTest {

    @Mock
    private EmailSenderStrategy smtpStrategy;

    @Mock
    private EmailSenderStrategy sesStrategy;

    private NotificationEmailProperties properties;
    private EmailSendCommand command;

    @BeforeEach
    void setUp() {
        properties = new NotificationEmailProperties();
        command = EmailSendCommand.builder()
                .to("tester@example.com")
                .subject("subject")
                .textBody("body")
                .build();
    }

    @Test
    void send_fallsBackWhenPrimaryFails() {
        when(sesStrategy.providerType()).thenReturn(EmailProviderType.SES);
        when(smtpStrategy.providerType()).thenReturn(EmailProviderType.SMTP);
        when(sesStrategy.send(any())).thenReturn(EmailSendResult.failure("SES", "temporary failure"));
        when(smtpStrategy.send(any())).thenReturn(EmailSendResult.success("SMTP", "smtp-msg-id"));

        RoutingEmailSender routingEmailSender = new RoutingEmailSender(properties, List.of(sesStrategy, smtpStrategy));
        routingEmailSender.initializeStrategyMap();

        properties.setProvider(EmailProviderType.SES);
        properties.setFallbackEnabled(true);
        properties.setFallbackProvider(EmailProviderType.SMTP);

        EmailSendResult result = routingEmailSender.send(command);

        assertTrue(result.isSuccess());
        verify(sesStrategy).send(any());
        verify(smtpStrategy).send(any());
    }

    @Test
    void send_returnsPrimaryFailureWhenFallbackDisabled() {
        when(sesStrategy.providerType()).thenReturn(EmailProviderType.SES);
        when(sesStrategy.send(any())).thenReturn(EmailSendResult.failure("SES", "service unavailable"));

        RoutingEmailSender routingEmailSender = new RoutingEmailSender(properties, List.of(sesStrategy));
        routingEmailSender.initializeStrategyMap();

        properties.setProvider(EmailProviderType.SES);
        properties.setFallbackEnabled(false);

        EmailSendResult result = routingEmailSender.send(command);

        assertFalse(result.isSuccess());
        verify(sesStrategy).send(any());
    }

    @Test
    void send_returnsFailureWhenStrategyMissing() {
        when(smtpStrategy.providerType()).thenReturn(EmailProviderType.SMTP);

        RoutingEmailSender routingEmailSender = new RoutingEmailSender(properties, List.of(smtpStrategy));
        routingEmailSender.initializeStrategyMap();

        properties.setProvider(EmailProviderType.SES);

        EmailSendResult result = routingEmailSender.send(command);

        assertFalse(result.isSuccess());
    }
}
