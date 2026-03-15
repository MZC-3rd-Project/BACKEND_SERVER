package com.example.notification.service.query;

import com.example.core.pagination.CursorResponse;
import com.example.core.pagination.CursorUtils;
import com.example.notification.dto.query.response.NotificationHistoryItemResponse;
import com.example.notification.dto.query.response.UnreadCountResponse;
import com.example.notification.service.unread.NotificationUnreadCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationHistoryReader notificationHistoryReader;

    @Spy
    private NotificationHistoryAssembler notificationHistoryAssembler;

    @Mock
    private NotificationUnreadCountService notificationUnreadCountService;

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Test
    void findMyNotifications_mapsHistoryViewsToResponse() {
        NotificationHistoryView first = new NotificationHistoryView(
                300L,
                "GENERAL",
                "IN_APP",
                "t1",
                "m1",
                null,
                null,
                false,
                null,
                LocalDateTime.now(),
                List.of(new NotificationHistoryView.NotificationDeliveryView(
                        "IN_APP",
                        "DELIVERED",
                        "IN_APP",
                        1,
                        null,
                        null,
                        null,
                        LocalDateTime.now()
                ))
        );
        NotificationHistoryView second = new NotificationHistoryView(
                200L,
                "GENERAL",
                "EMAIL",
                "t2",
                "m2",
                null,
                null,
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of()
        );

        when(notificationHistoryReader.findMyNotifications(10L, null, 2))
                .thenReturn(CursorResponse.of(List.of(first, second), CursorUtils.encode(200L)));

        CursorResponse<NotificationHistoryItemResponse> result =
                notificationQueryService.findMyNotifications(10L, null, 2);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo(CursorUtils.encode(200L));
        assertThat(result.getItems().get(0).getDeliveries()).hasSize(1);
        assertThat(result.getItems().get(0).getDeliveries().get(0).getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getItems().get(1).isRead()).isTrue();
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(notificationUnreadCountService.getOrLoad(10L)).thenReturn(7L);

        UnreadCountResponse response = notificationQueryService.getUnreadCount(10L);

        assertThat(response.getUnreadCount()).isEqualTo(7L);
    }
}
