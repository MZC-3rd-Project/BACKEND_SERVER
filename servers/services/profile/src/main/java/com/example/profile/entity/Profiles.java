package com.example.profile.entity;

import com.example.clients.auth.dto.profile.AuthSyncQuery;
import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.profile.crypto.EncryptedStringAttributeConverter;
import com.example.data.entity.BaseEntity;
import com.example.profile.dto.request.ProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Entity
@Builder
@Table(name = "profiles")
@AllArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profiles extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "email", nullable = false, length = 512)
    private String email;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "phone_number", length = 256)
    private String phoneNumber;

    @OneToOne(mappedBy = "profile", fetch = FetchType.LAZY)
    private ProfilesImage profileImage;

    public void updateProfile(ProfileRequest profile
    ){
        if (profile.getEmail() != null && !Objects.equals(this.email, profile.getEmail()))
            this.email = profile.getEmail();
        if (profile.getNickname() != null && !Objects.equals(this.nickname, profile.getNickname()))
            this.nickname = profile.getNickname();
        if (profile.getPhone() != null && !Objects.equals(this.phoneNumber, profile.getPhone()))
            this.phoneNumber = profile.getPhone();
    }

    public static Profiles create(AuthSyncQuery req){
        return Profiles.builder()
            .userId(req.profileInfo().userId())
            .email(req.profileInfo().email())
            .nickname(req.profileInfo().nickname())
            .phoneNumber(req.profileInfo().phoneNumber())
            .build();
    }

    public static Profiles createProjection(Long userId, String email, String nickname) {
        return Profiles.builder()
            .userId(userId)
            .email(email)
            .nickname(nickname)
            .build();
    }

    public void applyUserCreatedProjection(String email, String nickname) {
        if (StringUtils.hasText(email) && !Objects.equals(this.email, email)) {
            this.email = email;
        }
        if (StringUtils.hasText(nickname) && !Objects.equals(this.nickname, nickname)) {
            this.nickname = nickname;
        }
    }

    public void applyEmailChangedProjection(String newEmail) {
        if (StringUtils.hasText(newEmail) && !Objects.equals(this.email, newEmail)) {
            this.email = newEmail;
        }
    }

    public ProfilesImage getProfileImage() {
        return profileImage;
    }
}
