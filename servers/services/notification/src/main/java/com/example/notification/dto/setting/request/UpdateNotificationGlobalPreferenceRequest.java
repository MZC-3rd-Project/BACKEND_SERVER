package com.example.notification.dto.setting.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class UpdateNotificationGlobalPreferenceRequest {

    private Boolean globalEnabled;
    private String timezone;
    private Boolean quietHoursEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
}
