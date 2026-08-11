package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a eventseat.
 */
@Entity
@Table(name = "event_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_seat", columnNames = {"event_id", "section", "row_label", "seat_number"}),
        indexes = {
            @Index(name = "idx_event_seats_event", columnList = "event_id"),
            @Index(name = "idx_event_seats_status", columnList = "status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "ticket_type_id")
    private Long ticketTypeId;

    @Column(name = "row_label", nullable = false, length = 8)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, length = 8)
    private String seatNumber;

    @Column(nullable = false, length = 20)
    private String section;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Version
    @Column(nullable = false)
    private Integer version;

    @PrePersist
    void prePersist() {
        if (status == null) status = SeatStatus.AVAILABLE;
        if (version == null) version = 0;
    }
}
