package com.odoomaster.ticketing.ticketing.internal;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a ticket.
 */
@Entity
@Table(name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uk_tickets_qr", columnNames = "qr_code"),
        indexes = {
            @Index(name = "idx_tickets_order_item", columnList = "order_item_id"),
            @Index(name = "idx_tickets_user", columnList = "user_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_seat_id", nullable = false)
    private Long eventSeatId;

    @Column(name = "qr_code", nullable = false, unique = true, length = 64)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @PrePersist
    void prePersist() {
        if (issuedAt == null) issuedAt = Instant.now();
        if (status == null) status = TicketStatus.VALID;
    }
}
