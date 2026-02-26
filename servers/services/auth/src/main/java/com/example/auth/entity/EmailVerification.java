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

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static EmailVerification create(String email, String code, int ttlMinutes) {
        EmailVerification ev = new EmailVerification();
        ev.email = email;
        ev.code = code;
        ev.verified = false;
        ev.expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        ev.createdAt = LocalDateTime.now();
        return ev;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public void verify() {
        this.verified = true;
    }
}
