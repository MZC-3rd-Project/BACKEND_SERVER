package com.example.notification.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "notification_templates",
        indexes = {
                @Index(name = "idx_notification_templates_lookup",
                        columnList = "type, channel, locale, enabled")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_templates_type_channel_locale_version",
                        columnNames = {"type", "channel", "locale", "version"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class NotificationTemplate extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(name = "message_template", nullable = false, length = 2000)
    private String messageTemplate;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public static NotificationTemplate create(NotificationType type,
                                              NotificationChannel channel,
                                              String locale,
                                              String titleTemplate,
                                              String messageTemplate,
                                              int version,
                                              boolean enabled) {
        NotificationTemplate template = new NotificationTemplate();
        template.type = type;
        template.channel = channel;
        template.locale = locale;
        template.titleTemplate = titleTemplate;
        template.messageTemplate = messageTemplate;
        template.version = version;
        template.enabled = enabled;
        return template;
    }

    public void updateContent(String titleTemplate, String messageTemplate) {
        this.titleTemplate = titleTemplate;
        this.messageTemplate = messageTemplate;
    }

    public void activate() {
        this.enabled = true;
    }

    public void deactivate() {
        this.enabled = false;
    }
}
