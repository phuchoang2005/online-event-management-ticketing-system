package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.odoomaster.ticketing.shared.DomainException;
import java.util.Objects;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA entity mapping the persistence row for a event.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "event_category_map",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<EventCategory> categories = new HashSet<>();

    @Column(length = 255)
    private String organizer;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** A new event, held back from the public catalog until it is published. */
    public static Event draft(String title, String description, String location, String organizer,
                              String imageUrl, Instant startTime, Instant endTime, Instant now) {
        Event e = new Event();
        e.status = EventStatus.DRAFT;
        e.createdAt = Objects.requireNonNull(now, "now");
        e.describe(title, description, location, organizer, imageUrl);
        e.reschedule(startTime, endTime);
        return e;
    }

    // ── queries ─────────────────────────────────────────────────────────────────────────

    /** Whether tickets may be ordered. The one condition {@code EventCatalog.requireOnSale} checks. */
    public boolean isOnSale() {
        return status == EventStatus.PUBLISHED;
    }

    /** A published event is never deletable — cancel or complete it first. */
    public boolean isDeletable() {
        return status != EventStatus.PUBLISHED;
    }

    // ── transitions ─────────────────────────────────────────────────────────────────────

    /**
     * Put the event on sale.
     *
     * @param seatCount how many seats exist for it, supplied by the caller because seat inventory is
     *                  a different aggregate
     * @throws DomainException {@code EVENT_HAS_NO_SEATS} if there is nothing to sell
     */
    public void publish(long seatCount) {
        if (seatCount == 0) {
            throw new DomainException("EVENT_HAS_NO_SEATS", "Cannot publish an event with no seats.");
        }
        this.status = EventStatus.PUBLISHED;
    }

    /**
     * Pull the event back for editing.
     *
     * @param hasSoldSeats whether any seat has been sold, supplied by the caller
     * @throws DomainException {@code EVENT_HAS_TICKETS} if tickets are already out in the world
     */
    public void revertToDraft(boolean hasSoldSeats) {
        if (hasSoldSeats) {
            throw new DomainException("EVENT_HAS_TICKETS",
                    "Cannot revert to DRAFT: event already has issued tickets.");
        }
        this.status = EventStatus.DRAFT;
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    public void complete() {
        this.status = EventStatus.COMPLETED;
    }

    /** Apply an explicit status change, routed through the guard for that target. */
    public void changeStatusTo(EventStatus target, long seatCount, boolean hasSoldSeats) {
        switch (Objects.requireNonNull(target, "status")) {
            case PUBLISHED -> publish(seatCount);
            case DRAFT -> revertToDraft(hasSoldSeats);
            case CANCELLED -> cancel();
            case COMPLETED -> complete();
        }
    }

    public void describe(String title, String description, String location, String organizer,
                         String imageUrl) {
        if (title == null || title.isBlank()) {
            throw new DomainException("VALIDATION_FAILED", "Title is required.");
        }
        this.title = title.trim();
        this.description = description;
        this.location = location;
        this.organizer = organizer;
        this.imageUrl = imageUrl;
    }

    /**
     * @throws DomainException {@code VALIDATION_FAILED} if the event would end before it starts
     */
    public void reschedule(Instant startTime, Instant endTime) {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (!endTime.isAfter(startTime)) {
            throw new DomainException("VALIDATION_FAILED", "endTime must be after startTime.");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void categorise(Set<EventCategory> categories) {
        this.categories = categories == null ? new HashSet<>() : new HashSet<>(categories);
    }

    public void createdBy(Long userId) {
        this.createdBy = userId;
    }


    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = EventStatus.PUBLISHED;
    }

    public Set<String> getCategoryNames() {
        return categories == null ? Set.of() : categories.stream().map(EventCategory::getName).collect(Collectors.toSet());
    }
}
