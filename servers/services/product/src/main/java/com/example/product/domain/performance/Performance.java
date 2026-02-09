package com.example.product.domain.performance;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "performances")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @SnowflakeGenerated
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "venue", nullable = false, length = 200)
    private String venue;

    @Column(name = "performance_date", nullable = false)
    private LocalDate performanceDate;

    @Column(name = "performance_time", nullable = false)
    private LocalTime performanceTime;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    public static Performance create(Long itemId, String venue, LocalDate performanceDate,
                                     LocalTime performanceTime, int totalSeats) {
        Performance p = new Performance();
        p.itemId = itemId;
        p.venue = venue;
        p.performanceDate = performanceDate;
        p.performanceTime = performanceTime;
        p.totalSeats = totalSeats;
        return p;
    }

    public void update(String venue, LocalDate performanceDate, LocalTime performanceTime, int totalSeats) {
        this.venue = venue;
        this.performanceDate = performanceDate;
        this.performanceTime = performanceTime;
        this.totalSeats = totalSeats;
    }
}
