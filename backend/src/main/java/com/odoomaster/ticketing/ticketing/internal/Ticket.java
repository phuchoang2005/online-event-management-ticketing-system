package com.odoomaster.ticketing.ticketing.internal;

import jakarta.persistence.*;
import java.util.Objects;
import com.odoomaster.ticketing.shared.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

    /** Issue a VALID ticket for one paid order line, with a fresh QR code. */
    public static Ticket issue(Long orderItemId, Long userId, Long eventId, Long eventSeatId,
                               QrCode qrCode, Instant now) {
        Ticket ticket = new Ticket();
        ticket.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        ticket.userId = Objects.requireNonNull(userId, "userId");
        ticket.eventId = Objects.requireNonNull(eventId, "eventId");
        ticket.eventSeatId = Objects.requireNonNull(eventSeatId, "eventSeatId");
        ticket.qrCode = Objects.requireNonNull(qrCode, "qrCode").value();
        ticket.status = TicketStatus.VALID;
        ticket.issuedAt = Objects.requireNonNull(now, "now");
        return ticket;
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    /**
     * Whether the gate may admit this ticket.
     *
     * <p>{@code existingCheckIn} is passed in rather than looked up: whether a check-in row exists is
     * a fact about another table, and the aggregate does not get to query.
     */
    public boolean isScannable(boolean existingCheckIn) {
        return status == TicketStatus.VALID && !existingCheckIn;
    }

    /**
     * Admit the holder.
     *
     * @throws DomainException {@code ALREADY_USED} if the ticket has already been scanned,
     *         {@code TICKET_NOT_VALID} if it was cancelled. The order of these two checks is what
     *         tells a gate steward whether to look for a duplicate or a refund.
     */
    public void markUsed() {
        if (status == TicketStatus.USED) {
            throw new DomainException("ALREADY_USED", "Ticket already checked in.");
        }
        if (status != TicketStatus.VALID) {
            throw new DomainException("TICKET_NOT_VALID", "Ticket not in VALID state.");
        }
        this.status = TicketStatus.USED;
    }

    /**
     * Withdraw the ticket so its seat can be resold. A no-op if already cancelled.
     *
     * @throws DomainException {@code TICKET_ALREADY_USED} if the holder has already been admitted —
     *         deleting a ticket that walked through the gate would erase attendance history
     */
    public void cancel() {
        if (status == TicketStatus.CANCELLED) {
            return;
        }
        if (status == TicketStatus.USED) {
            throw new DomainException("TICKET_ALREADY_USED",
                    "Cannot delete a ticket that has already been checked in.");
        }
        this.status = TicketStatus.CANCELLED;
    }

    @PrePersist
    void prePersist() {
        if (issuedAt == null) issuedAt = Instant.now();
        if (status == null) status = TicketStatus.VALID;
    }
}
