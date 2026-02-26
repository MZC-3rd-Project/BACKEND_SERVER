package com.example.profile.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.profile.dto.request.ProfileCreateRequest;
import com.example.profile.dto.request.ProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

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

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "delivery", length = 100)
    private String delivery; // 테이블 따로 만들어야 될거 같음

    public void updateProfile(ProfileRequest profile
    ){
        if (profile.getEmail() != null && !Objects.equals(this.email, profile.getEmail()))
            this.email = profile.getEmail();
        if (profile.getNickname() != null && !Objects.equals(this.nickname, profile.getNickname()))
            this.nickname = profile.getNickname();
        if (profile.getPhone() != null && !Objects.equals(this.phone, profile.getPhone()))
            this.phone = profile.getPhone();
        if (profile.getDelivery() != null && !Objects.equals(this.delivery, profile.getDelivery()))
            this.delivery = profile.getDelivery();
    }

    public static Profiles create(ProfileCreateRequest req){
        return Profiles.builder()
            .userId(req.getUserId())
            .email(req.getEmail())
            .nickname(req.getNickname())
            .phone(req.getPhone())
            .delivery(req.getDelivery())
            .build();
    }


}
