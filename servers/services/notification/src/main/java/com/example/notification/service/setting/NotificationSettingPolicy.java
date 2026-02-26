package com.example.notification.service.setting;

import com.example.notification.entity.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationSettingPolicy {

    private static final List<NotificationChannel> SUPPORTED_CHANNELS = List.of(
            NotificationChannel.IN_APP,
            NotificationChannel.EMAIL,
            NotificationChannel.SMS,
            NotificationChannel.KAKAO,
            NotificationChannel.PUSH
    );

    public List<NotificationChannel> supportedChannels() {
        return SUPPORTED_CHANNELS;
    }

    public boolean isDefaultEnabled(NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP || channel == NotificationChannel.EMAIL;
    }
}
