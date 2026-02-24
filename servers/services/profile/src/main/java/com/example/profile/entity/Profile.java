package com.example.profile.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profile extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long id;

    private String name;
    private String description;

    public static Profile create(String name, String description) {
        return Profile.builder()
            .name(name)
            .description(description)
            .build();
    }

}
