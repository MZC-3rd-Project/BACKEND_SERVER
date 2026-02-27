package com.example.auth.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "user_status_histories", indexes = {
        @Index(name = "idx_user_status_histories_user_id", columnList = "user_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStatusHistory extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private UserStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private UserStatus newStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    public static UserStatusHistory create(Long userId, UserStatus previousStatus,
                                           UserStatus newStatus, String reason, Long changedBy) {
        UserStatusHistory history = new UserStatusHistory();
        history.userId = userId;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.reason = reason;
        history.changedBy = changedBy;
        return history;
    }
}
