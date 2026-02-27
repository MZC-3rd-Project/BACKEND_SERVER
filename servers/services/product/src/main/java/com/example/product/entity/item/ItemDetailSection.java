package com.example.product.entity.item;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import com.example.product.entity.converter.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

@Entity
@Table(name = "item_detail_sections", indexes = {
        @Index(name = "idx_item_detail_sections_item_id", columnList = "item_id")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemDetailSection extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "section_title", length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "highlights_json", nullable = false, columnDefinition = "TEXT")
    private List<String> highlights;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    public static ItemDetailSection create(Long itemId, String title, String description,
                                           String imageUrl, List<String> highlights, int sortOrder) {
        ItemDetailSection section = new ItemDetailSection();
        section.itemId = itemId;
        section.title = title;
        section.description = description;
        section.imageUrl = imageUrl;
        section.highlights = highlights == null ? List.of() : List.copyOf(highlights);
        section.sortOrder = sortOrder;
        return section;
    }
}
