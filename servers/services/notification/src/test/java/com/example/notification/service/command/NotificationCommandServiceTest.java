package com.example.notification.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.DomainEvent;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.notification.dto.command.request.CreateNotificationRequest;
import com.example.notification.dto.command.response.NotificationDispatchResponse;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationTemplate;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.NotificationDeliveryRequestedEvent;
import com.example.notification.exception.NotificationErrorCode;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.content.NotificationContentSanitizer;
import com.example.notification.service.template.NotificationTemplateResolver;
import com.example.notification.service.template.TemplateRenderService;
import com.example.notification.service.unread.NotificationUnreadCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationTemplateResolver notificationTemplateResolver;

    @Mock
    private TemplateRenderService templateRenderService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private NotificationUnreadCountService notificationUnreadCountService;

    @Spy
    private NotificationContentSanitizer notificationContentSanitizer;

    @InjectMocks
    private NotificationCommandService notificationCommandService;

    @Test
    void createAndSend_returnsDeduplicatedWhenSameKeyExists() {
        Notification existing = Notification.create(
                10L, 20L, NotificationType.GENERAL, NotificationChannel.IN_APP,
                "title", "message", null, null, "evt-1", "dedupe-1", null
        );
        ReflectionTestUtils.setField(existing, "id", 999L);

        when(notificationRepository.findByDedupeKey("dedupe-1")).thenReturn(Optional.of(existing));

        CreateNotificationRequest request = new CreateNotificationRequest();
        ReflectionTestUtils.setField(request, "recipientId", 10L);
        ReflectionTestUtils.setField(request, "type", "GENERAL");
        ReflectionTestUtils.setField(request, "dedupeKey", "dedupe-1");

        NotificationDispatchResponse response = notificationCommandService.createAndSend(request, 10L);

        assertThat(response.isDeduplicated()).isTrue();
        assertThat(response.getNotificationId()).isEqualTo(999L);
        verify(eventPublisher).publish(any(DomainEvent.class), any(EventMetadata.class));
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationUnreadCountService, never()).increase(any());
    }

    @Test
    void createAndSend_savesNotificationAndPublishesDeliveryEvents() {
        when(notificationRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(notificationTemplateResolver.resolve(
                eq(NotificationType.PAYMENT), eq(NotificationChannel.IN_APP), eq("ko-KR")
        )).thenReturn(Optional.of(NotificationTemplate.create(
                NotificationType.PAYMENT,
                NotificationChannel.IN_APP,
                "ko-KR",
                "{{userName}}님 결제가 완료됐어요",
                "{{amount}}원 결제 성공",
                1,
                true
        )));
        when(templateRenderService.render(eq("{{userName}}님 결제가 완료됐어요"), any()))
                .thenReturn("딩주님 결제가 완료됐어요");
        when(templateRenderService.render(eq("{{amount}}원 결제 성공"), any()))
                .thenReturn("10000원 결제 성공");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12345L);
            return saved;
        });

        CreateNotificationRequest request = new CreateNotificationRequest();
        ReflectionTestUtils.setField(request, "recipientId", 77L);
        ReflectionTestUtils.setField(request, "type", "PAYMENT");
        ReflectionTestUtils.setField(request, "channels", List.of("IN_APP", "EMAIL"));
        ReflectionTestUtils.setField(request, "locale", "ko-KR");
        ReflectionTestUtils.setField(request, "emailTo", "ding@example.com");
        ReflectionTestUtils.setField(request, "externalEventId", "evt-100");
        ReflectionTestUtils.setField(request, "variables", Map.of("userName", "딩주", "amount", 10000));

        NotificationDispatchResponse response = notificationCommandService.createAndSend(request, 77L);

        assertThat(response.isDeduplicated()).isFalse();
        assertThat(response.getNotificationId()).isEqualTo(12345L);
        assertThat(response.getDeliveredChannels()).isEmpty();
        assertThat(response.getFailedChannels()).isEmpty();
        assertThat(response.getStatus()).isEqualTo("CREATED");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        verify(notificationUnreadCountService).increase(77L);
        assertThat(captor.getValue().getRecipientId()).isEqualTo(77L);
        assertThat(captor.getValue().getTitle()).isEqualTo("딩주님 결제가 완료됐어요");

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, times(2)).publish(eventCaptor.capture(), any(EventMetadata.class));

        assertThat(eventCaptor.getAllValues())
                .allMatch(event -> event instanceof NotificationDeliveryRequestedEvent);
        assertThat(eventCaptor.getAllValues())
                .extracting(event -> event.getPayload().get("channel"))
                .containsExactly("IN_APP", "EMAIL");
    }

    @Test
    void createAndSend_throwsWhenRecipientDiffersFromRequester() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        ReflectionTestUtils.setField(request, "recipientId", 200L);
        ReflectionTestUtils.setField(request, "type", "GENERAL");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationCommandService.createAndSend(request, 100L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.FORBIDDEN_RECIPIENT);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createAndSend_throwsWhenActorIsSpoofed() {
        CreateNotificationRequest request = new CreateNotificationRequest();
        ReflectionTestUtils.setField(request, "recipientId", 100L);
        ReflectionTestUtils.setField(request, "actorId", 999L);
        ReflectionTestUtils.setField(request, "type", "GENERAL");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationCommandService.createAndSend(request, 100L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.FORBIDDEN_ACTOR);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createAndSend_sanitizesHtmlContentBeforePersisting() {
        when(notificationRepository.findByDedupeKey(any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 56789L);
            return saved;
        });

        CreateNotificationRequest request = new CreateNotificationRequest();
        ReflectionTestUtils.setField(request, "recipientId", 77L);
        ReflectionTestUtils.setField(request, "type", "GENERAL");
        ReflectionTestUtils.setField(request, "title", "<script>alert('x')</script>");
        ReflectionTestUtils.setField(request, "message", "<img src=x onerror=alert('x')>");
        ReflectionTestUtils.setField(request, "variables", Map.of("itemTitle", "<b>굿즈</b>"));

        notificationCommandService.createAndSend(request, 77L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
        assertThat(saved.getMessage()).isEqualTo("&lt;img src=x onerror=alert(&#39;x&#39;)&gt;");
        assertThat(saved.getPayload()).containsEntry("itemTitle", "&lt;b&gt;굿즈&lt;/b&gt;");
    }
}
