package com.example.auth.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications", indexes = {
        @Index(name = "idx_email_verifications_email", columnList = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static EmailVerification create(String email, String code, LocalDateTime expiresAt) {
        EmailVerification ev = new EmailVerification();
        ev.email = email;
        ev.code = code;
        ev.verified = false;
        ev.expiresAt = expiresAt;
        ev.createdAt = LocalDateTime.now();
        return ev;
    }

    public void verify() {
        if (isExpired()) {
            throw new IllegalStateException("인증 코드가 만료되었습니다");
        }
        this.verified = true;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
