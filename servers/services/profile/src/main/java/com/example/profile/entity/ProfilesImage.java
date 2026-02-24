package com.example.profile.entity;

import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;


@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@Entity
@Table(name = "profile_images")
public class ProfilesImage extends BaseEntity {

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private Profiles profile;

    private String mediaId;

    private Integer sortOrder = 0;

}
