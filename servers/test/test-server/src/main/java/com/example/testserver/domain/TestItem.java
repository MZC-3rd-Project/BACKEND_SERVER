package com.example.testserver.domain;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestItem extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    public static TestItem create(String name, String description) {
        TestItem item = new TestItem();
        item.name = name;
        item.description = description;
        return item;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
