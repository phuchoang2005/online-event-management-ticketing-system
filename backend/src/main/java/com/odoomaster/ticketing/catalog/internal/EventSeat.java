package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.shared.DomainException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single sellable seat for one event, and the aggregate root of the system's
 * {@code AVAILABLE → LOCKED → SOLD} state machine.
 *
 * <p>This class owns every transition. Before ADR-0013 the rules lived in {@code SeatInventoryImpl}
 * while {@code SeatLockSweeperJob} and {@code AdminEventService} reached past it and wrote
 * {@code setStatus(...)} directly — three independent implementations of "what may happen to a
 * seat", one of which would happily reprice a seat that had already been sold. There are no setters
 * now: the only way a seat's status, price or lock changes is through a method here that checks the
 * invariant first.
 *
 * <p><strong>Concurrency.</strong> This is the crux of the Golden Hour. The {@code @Version} column
 * gives optimistic locking against concurrent writers, the {@code uk_event_seat} unique constraint
 * prevents duplicate seats, and callers must invoke these methods inside the ordering transaction so
 * the check and the write commit together. The aggregate never reads the clock — callers pass an
 * {@code Instant} from the application's {@code Clock} bean — which is what makes lock expiry
 * testable without sleeping.
 *
 * @see LockPolicy for the hold duration
 * @see SeatLock for the hold itself
 */
@Entity
@Table(name = "event_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_seat", columnNames = {"event_id", "section", "row_label", "seat_number"}),
        indexes = {
            @Index(name = "idx_event_seats_event", columnList = "event_id"),
            @Index(name = "idx_event_seats_status", columnList = "status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

    /**
     * The current hold, or {@code null} when the seat is not held. Never exposed: Hibernate reads an
     * all-null embeddable back as {@code null}, so callers go through {@link #lockedBy()},
     * {@link #lockedUntil()} and {@link #isLockExpiredAt(Instant)}.
     */
    @Embedded
    private SeatLock lock;

    @Version
    @Column(nullable = false)
    private Integer version;

    /** A brand-new AVAILABLE seat. The only way application code creates one. */
    public static EventSeat create(Long eventId, Long seatId, Long ticketTypeId,
                                   String section, String rowLabel, String seatNumber,
                                   BigDecimal price) {
        EventSeat seat = new EventSeat();
        seat.eventId = Objects.requireNonNull(eventId, "eventId");
        seat.seatId = Objects.requireNonNull(seatId, "seatId");
        seat.ticketTypeId = ticketTypeId;
        seat.section = Objects.requireNonNull(section, "section");
        seat.rowLabel = Objects.requireNonNull(rowLabel, "rowLabel");
        seat.seatNumber = Objects.requireNonNull(seatNumber, "seatNumber");
        seat.price = requireNonNegative(price);
        seat.status = SeatStatus.AVAILABLE;
        seat.version = 0;
        return seat;
    }

    // ── queries ─────────────────────────────────────────────────────────────────────────

    /** @return the holder of the current lock, or {@code null} if the seat is not held */
    public Long lockedBy() {
        return lock == null ? null : lock.lockedBy();
    }

    /** @return when the current hold lapses, or {@code null} if the seat is not held */
    public Instant lockedUntil() {
        return lock == null ? null : lock.lockedUntil();
    }

    /** Whether a hold exists and has lapsed at {@code now}. False when there is no hold at all. */
    public boolean isLockExpiredAt(Instant now) {
        return lock != null && lock.hasExpiredAt(now);
    }

    public boolean isSold() {
        return status == SeatStatus.SOLD;
    }

    public boolean belongsTo(Long eventId) {
        return Objects.equals(this.eventId, eventId);
    }

    /**
     * Whether a buyer may take this seat at {@code now} — either it is free, or its previous hold
     * has lapsed and the sweeper simply has not got to it yet.
     */
    public boolean isLockableAt(Instant now) {
        return status == SeatStatus.AVAILABLE
                || (status == SeatStatus.LOCKED && isLockExpiredAt(now));
    }

    /** Human-readable seat identity for error messages, e.g. {@code A-12}. */
    public String label() {
        return rowLabel + "-" + seatNumber;
    }

    // ── transitions ─────────────────────────────────────────────────────────────────────

    /** @throws DomainException {@code SEAT_NOT_IN_EVENT} if this seat belongs to a different event */
    public void requireBelongsTo(Long eventId) {
        if (!belongsTo(eventId)) {
            throw new DomainException("SEAT_NOT_IN_EVENT", "Seat " + id + " is not in this event.");
        }
    }

    /**
     * Hold this seat for {@code userId} until the policy's TTL elapses.
     *
     * @throws DomainException {@code SEAT_TAKEN} if the seat is sold, or held by someone whose hold
     *                         has not yet lapsed
     */
    public void lockFor(Long userId, Instant now, LockPolicy policy) {
        if (!isLockableAt(now)) {
            throw new DomainException("SEAT_TAKEN", "Seat " + label() + " is no longer available.");
        }
        this.status = SeatStatus.LOCKED;
        this.lock = SeatLock.heldBy(userId, policy.expiryFrom(now));
    }

    /**
     * Complete the sale, clearing the hold.
     *
     * <p>The guard order matters and is asserted by {@code SeatInventoryReliabilityTest}: an
     * already-sold seat reports {@code SEAT_TAKEN} (someone else bought it), while a seat whose hold
     * lapsed reports {@code LOCK_EXPIRED} (you waited too long). Swapping them would tell buyers the
     * wrong story about why their checkout failed.
     *
     * @throws DomainException {@code SEAT_TAKEN} if already sold, {@code LOCK_EXPIRED} if the hold lapsed
     */
    public void markSold(Instant now) {
        if (status == SeatStatus.SOLD) {
            throw new DomainException("SEAT_TAKEN", "Seat already sold.");
        }
        if (status != SeatStatus.AVAILABLE && isLockExpiredAt(now)) {
            throw new DomainException("LOCK_EXPIRED", "Seat lock expired; please re-select seats.");
        }
        this.status = SeatStatus.SOLD;
        this.lock = null;
    }

    /** Return a held seat to the pool. A no-op unless the seat is currently {@code LOCKED}. */
    public void releaseHold() {
        if (status == SeatStatus.LOCKED) {
            this.status = SeatStatus.AVAILABLE;
            this.lock = null;
        }
    }

    /** Return a sold seat to the pool after a ticket cancellation. A no-op unless {@code SOLD}. */
    public void releaseSale() {
        if (status == SeatStatus.SOLD) {
            this.status = SeatStatus.AVAILABLE;
            this.lock = null;
        }
    }

    /**
     * The sweeper's transition: reclaim this seat if its hold has lapsed.
     *
     * <p>Expressed as "did anything change?" so the sweeper can collect exactly the affected events
     * for cache eviction instead of assuming every row it loaded was stale.
     *
     * @return {@code true} if the seat was reclaimed
     */
    public boolean releaseExpiredLock(Instant now) {
        if (status != SeatStatus.LOCKED || !isLockExpiredAt(now)) {
            return false;
        }
        this.status = SeatStatus.AVAILABLE;
        this.lock = null;
        return true;
    }

    /**
     * Change the price of an unsold seat.
     *
     * <p>Repricing a {@code SOLD} seat is refused because {@code sumSoldPriceForEvent} computes an
     * event's realised revenue as the sum of its sold seats' prices — so an ordinary admin edit used
     * to silently rewrite money that had already been reported and collected.
     *
     * @throws DomainException {@code SEAT_SOLD_IMMUTABLE} if the seat has been sold
     */
    public void reprice(BigDecimal newPrice) {
        if (isSold()) {
            throw new DomainException("SEAT_SOLD_IMMUTABLE",
                    "Cannot change the price of seat " + label() + ": it has already been sold.");
        }
        this.price = requireNonNegative(newPrice);
    }

    /**
     * Rename the section this seat sits in. Permitted in any state — a section's name is a display
     * label, not part of the sale, so renaming "VIP" to "Premium" must not be blocked by sold seats.
     */
    public void relabelSection(String newSection) {
        this.section = Objects.requireNonNull(newSection, "section");
    }

    @PrePersist
    void prePersist() {
        if (status == null) status = SeatStatus.AVAILABLE;
        if (version == null) version = 0;
    }

    private static BigDecimal requireNonNegative(BigDecimal price) {
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) {
            throw new DomainException("VALIDATION_FAILED", "Seat price cannot be negative.");
        }
        return price;
    }
}
