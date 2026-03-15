package com.example.notification.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationUserPreferenceTest {

    @Test
    void createDefault_setsExpectedDefaults() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);

        assertThat(preference.getUserId()).isEqualTo(101L);
        assertThat(preference.isGlobalEnabled()).isTrue();
        assertThat(preference.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(preference.isQuietHoursEnabled()).isFalse();
        assertThat(preference.getQuietHoursStart()).isEqualTo(LocalTime.of(22, 0));
        assertThat(preference.getQuietHoursEnd()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void isInQuietHours_returnsFalseWhenQuietHoursDisabled() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);
        preference.updateQuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0));
        preference.updateQuietHoursEnabled(false);

        boolean quietHours = preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 23, 30));

        assertThat(quietHours).isFalse();
    }

    @Test
    void isInQuietHours_handlesSameDayRange() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);
        preference.updateTimezone("Asia/Seoul");
        preference.updateQuietHoursEnabled(true);
        preference.updateQuietHours(LocalTime.of(9, 0), LocalTime.of(17, 0));

        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 8, 59))).isFalse();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 9, 0))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 16, 59))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 17, 0))).isFalse();
    }

    @Test
    void isInQuietHours_handlesOvernightRange() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);
        preference.updateTimezone("Asia/Seoul");
        preference.updateQuietHoursEnabled(true);
        preference.updateQuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0));

        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 21, 59))).isFalse();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 22, 0))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 15, 7, 59))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 15, 8, 0))).isFalse();
    }

    @Test
    void isInQuietHours_treatsEqualStartAndEndAsAlwaysQuiet() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);
        preference.updateTimezone("Asia/Seoul");
        preference.updateQuietHoursEnabled(true);
        preference.updateQuietHours(LocalTime.of(0, 0), LocalTime.of(0, 0));

        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 0, 0))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 12, 0))).isTrue();
        assertThat(preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 23, 59))).isTrue();
    }

    @Test
    void isInQuietHours_fallsBackToSystemDefaultWhenTimezoneIsInvalid() {
        NotificationUserPreference preference = NotificationUserPreference.createDefault(101L);
        preference.updateTimezone("Not/AZone");
        preference.updateQuietHoursEnabled(true);
        preference.updateQuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0));

        boolean quietHours = preference.isInQuietHours(LocalDateTime.of(2026, 3, 14, 23, 30));

        assertThat(quietHours).isTrue();
    }
}
