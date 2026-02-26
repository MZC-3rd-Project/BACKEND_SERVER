package com.example.auth.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_status", columnList = "status"),
        @Index(name = "idx_users_auth_provider", columnList = "auth_provider"),
        @Index(name = "idx_users_email", columnList = "email")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 36)
    private String keycloakId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public static User create(String keycloakId, String email, String nickname) {
        User user = new User();
        user.keycloakId = keycloakId;
        user.email = email;
        user.nickname = nickname;
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.authProvider = AuthProvider.KEYCLOAK;
        return user;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        softDelete();
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        restore();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public void linkSocialProvider(AuthProvider provider) {
        this.authProvider = provider;
    }
}
