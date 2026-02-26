package com.example.notification.service.realtime;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationChannel;
import com.example.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SseNotificationEventTest {

    @Test
    void from_escapesHtmlSensitiveFields() {
        Notification notification = Notification.create(
                10L,
                10L,
                NotificationType.GENERAL,
                NotificationChannel.IN_APP,
                "<script>alert('x')</script>",
                "<img src=x onerror=alert('x')>",
                "<b>ITEM</b>",
                "<a>1</a>",
                "evt-1",
                "dedupe-1",
                null
        );
        ReflectionTestUtils.setField(notification, "id", 100L);

        SseNotificationEvent event = SseNotificationEvent.from(notification);

        assertThat(event.getTitle()).isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
        assertThat(event.getMessage()).isEqualTo("&lt;img src=x onerror=alert(&#39;x&#39;)&gt;");
        assertThat(event.getReferenceType()).isEqualTo("&lt;b&gt;ITEM&lt;/b&gt;");
        assertThat(event.getReferenceId()).isEqualTo("&lt;a&gt;1&lt;/a&gt;");
    }
}
