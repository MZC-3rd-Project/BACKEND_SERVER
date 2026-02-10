package com.example.product.entity.category;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_categories_parent_id", columnList = "parent_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "depth", nullable = false)
    private Integer depth;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static Category createRoot(String name, int sortOrder) {
        Category category = new Category();
        category.name = name;
        category.parentId = null;
        category.depth = 0;
        category.sortOrder = sortOrder;
        return category;
    }

    public static Category createChild(String name, Long parentId, int parentDepth, int sortOrder) {
        Category category = new Category();
        category.name = name;
        category.parentId = parentId;
        category.depth = parentDepth + 1;
        category.sortOrder = sortOrder;
        return category;
    }

    public void update(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
