package com.example.orderquery.entity;

import com.example.data.entity.BaseEntity;
import com.example.orderquery.entity.Enums.TicketStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private Long orderItemId;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "venue", nullable = false, length = 200)
    private String venue;

    @Column(name = "seat_info", length = 100)
    private String seatInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;
}
