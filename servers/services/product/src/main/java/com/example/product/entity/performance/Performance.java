package com.example.product.entity.performance;

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
@Table(name = "performances", indexes = {
        @Index(name = "idx_performances_item_id", columnList = "item_id", unique = true)
})
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

    @Column(name = "running_time_minutes")
    private Integer runningTimeMinutes;

    @Column(name = "age_limit", length = 50)
    private String ageLimit;

    @Column(name = "venue_address", length = 255)
    private String venueAddress;

    @Column(name = "booking_notice", columnDefinition = "TEXT")
    private String bookingNotice;

    @Column(name = "organizer", length = 100)
    private String organizer;

    @Column(name = "host", length = 100)
    private String host;

    public static Performance create(Long itemId, String venue, LocalDate performanceDate,
                                     LocalTime performanceTime, int totalSeats) {
        return create(itemId, venue, performanceDate, performanceTime, totalSeats,
                null, null, null, null, null, null);
    }

    public static Performance create(Long itemId, String venue, LocalDate performanceDate,
                                     LocalTime performanceTime, int totalSeats,
                                     Integer runningTimeMinutes, String ageLimit,
                                     String venueAddress, String bookingNotice,
                                     String organizer, String host) {
        Performance p = new Performance();
        p.itemId = itemId;
        p.venue = venue;
        p.performanceDate = performanceDate;
        p.performanceTime = performanceTime;
        p.totalSeats = totalSeats;
        p.runningTimeMinutes = runningTimeMinutes;
        p.ageLimit = ageLimit;
        p.venueAddress = venueAddress;
        p.bookingNotice = bookingNotice;
        p.organizer = organizer;
        p.host = host;
        return p;
    }

    public void update(String venue, LocalDate performanceDate, LocalTime performanceTime, int totalSeats) {
        update(venue, performanceDate, performanceTime, totalSeats,
                this.runningTimeMinutes, this.ageLimit, this.venueAddress,
                this.bookingNotice, this.organizer, this.host);
    }

    public void update(String venue, LocalDate performanceDate, LocalTime performanceTime, int totalSeats,
                       Integer runningTimeMinutes, String ageLimit, String venueAddress,
                       String bookingNotice, String organizer, String host) {
        this.venue = venue;
        this.performanceDate = performanceDate;
        this.performanceTime = performanceTime;
        this.totalSeats = totalSeats;
        this.runningTimeMinutes = runningTimeMinutes;
        this.ageLimit = ageLimit;
        this.venueAddress = venueAddress;
        this.bookingNotice = bookingNotice;
        this.organizer = organizer;
        this.host = host;
    }
}
