package com.example.product.domain.performance;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "cast_members")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CastMember extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    public static CastMember create(Long performanceId, String name, String role, String profileImageUrl) {
        CastMember cm = new CastMember();
        cm.performanceId = performanceId;
        cm.name = name;
        cm.role = role;
        cm.profileImageUrl = profileImageUrl;
        return cm;
    }

    public void update(String name, String role, String profileImageUrl) {
        this.name = name;
        this.role = role;
        this.profileImageUrl = profileImageUrl;
    }
}
