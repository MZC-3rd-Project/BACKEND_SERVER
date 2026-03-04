package com.example.store.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
@Entity
@Builder
@AllArgsConstructor
@Table(name = "store_profiles")
public class StoreProfile extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long Id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Stores store;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public static StoreProfile create(Stores store, String description) {
        StoreProfile sp = new StoreProfile();
        sp.store = store;
        sp.description = description;
        return sp;
    }

    public void updateDescription(String description) {
        this.description = description;
    }
}
