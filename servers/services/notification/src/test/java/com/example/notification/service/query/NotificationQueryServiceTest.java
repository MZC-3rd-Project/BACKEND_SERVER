package com.example.notification.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.notification.dto.query.response.NotificationHistoryItemResponse;
import com.example.notification.dto.query.response.UnreadCountResponse;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationDelivery;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationDeliveryRepository;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.unread.NotificationUnreadCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationUnreadCountService notificationUnreadCountService;

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Test
    void findMyNotifications_returnsCursorPage() {
        Notification first = Notification.create(10L, 1L, NotificationType.GENERAL, NotificationChannel.IN_APP,
                "t1", "m1", null, null, null, "d1", null);
        Notification second = Notification.create(10L, 1L, NotificationType.GENERAL, NotificationChannel.IN_APP,
                "t2", "m2", null, null, null, "d2", null);
        Notification third = Notification.create(10L, 1L, NotificationType.GENERAL, NotificationChannel.IN_APP,
                "t3", "m3", null, null, null, "d3", null);
        ReflectionTestUtils.setField(first, "id", 300L);
        ReflectionTestUtils.setField(second, "id", 200L);
        ReflectionTestUtils.setField(third, "id", 100L);

        when(notificationRepository.findByRecipientIdWithCursor(
                eq(10L), eq(null), eq(PageRequest.of(0, 3))
        )).thenReturn(List.of(first, second, third));

        NotificationDelivery firstDelivery = NotificationDelivery.createPending(
                300L,
                NotificationChannel.IN_APP,
                "IN_APP",
                null
        );
        ReflectionTestUtils.setField(firstDelivery, "id", 900L);
        firstDelivery.markSent("IN_APP:900");
        firstDelivery.markDelivered();

        NotificationDelivery secondDelivery = NotificationDelivery.createPending(
                200L,
                NotificationChannel.EMAIL,
                "EMAIL",
                "test@example.com"
        );
        ReflectionTestUtils.setField(secondDelivery, "id", 901L);
        secondDelivery.markRetry("EMAIL_SEND_FAILED", "smtp timeout", null);

        when(notificationDeliveryRepository.findByNotificationIdInOrderByNotificationIdAscIdAsc(
                eq(List.of(300L, 200L))
        )).thenReturn(List.of(firstDelivery, secondDelivery));

        CursorResponse<NotificationHistoryItemResponse> result =
                notificationQueryService.findMyNotifications(10L, null, 2);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo(CursorUtils.encode(200L));
        assertThat(result.getItems().get(0).getDeliveries()).hasSize(1);
        assertThat(result.getItems().get(0).getDeliveries().get(0).getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getItems().get(1).getDeliveries()).hasSize(1);
        assertThat(result.getItems().get(1).getDeliveries().get(0).getStatus()).isEqualTo("RETRYING");
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(notificationUnreadCountService.getOrLoad(10L)).thenReturn(7L);

        UnreadCountResponse response = notificationQueryService.getUnreadCount(10L);

        assertThat(response.getUnreadCount()).isEqualTo(7L);
    }
}
