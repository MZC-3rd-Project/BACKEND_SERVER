package com.example.profile.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "profile_images")
public class ProfilesImage extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Profiles profile;

    @Column(name = "media_id", nullable = false, length = 500)
    private Long mediaId;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public void updateMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }


}
