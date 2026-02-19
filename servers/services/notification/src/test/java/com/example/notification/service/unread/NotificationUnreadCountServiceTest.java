package com.example.notification.service.unread;

import com.example.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationUnreadCountServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationUnreadCountService notificationUnreadCountService;

    @Test
    void getOrLoad_returnsCachedValueWhenExists() {
        notificationUnreadCountService = new NotificationUnreadCountService(stringRedisTemplate, notificationRepository);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notification:unread:10")).thenReturn("7");

        long unread = notificationUnreadCountService.getOrLoad(10L);

        assertThat(unread).isEqualTo(7L);
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(10L);
    }

    @Test
    void getOrLoad_loadsFromDbWhenCacheMiss() {
        notificationUnreadCountService = new NotificationUnreadCountService(stringRedisTemplate, notificationRepository);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notification:unread:10")).thenReturn(null);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(10L)).thenReturn(3L);

        long unread = notificationUnreadCountService.getOrLoad(10L);

        assertThat(unread).isEqualTo(3L);
        verify(valueOperations).set(eq("notification:unread:10"), eq("3"), any(Duration.class));
    }

    @Test
    void increase_updatesCacheCounter() {
        notificationUnreadCountService = new NotificationUnreadCountService(stringRedisTemplate, notificationRepository);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("notification:unread:10")).thenReturn(2L);

        notificationUnreadCountService.increase(10L);

        verify(valueOperations).increment("notification:unread:10");
        verify(stringRedisTemplate).expire(eq("notification:unread:10"), any(Duration.class));
    }

    @Test
    void decreaseSafely_executesLuaScript() {
        notificationUnreadCountService = new NotificationUnreadCountService(stringRedisTemplate, notificationRepository);
        notificationUnreadCountService.decreaseSafely(10L, 2L);

        verify(stringRedisTemplate).execute(
                any(),
                eq(Collections.singletonList("notification:unread:10")),
                eq("2"),
                any()
        );
    }
}
