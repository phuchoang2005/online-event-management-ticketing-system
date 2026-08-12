package com.odoomaster.ticketing.ticketing.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a checkin.
 */
@Entity
@Table(name = "check_ins",
        uniqueConstraints = @UniqueConstraint(name = "uk_check_ins_ticket", columnNames = "ticket_id"),
        indexes = @Index(name = "idx_checkin_at", columnList = "checked_in_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "checked_in_by", nullable = false)
    private Long checkedInBy;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInStatus status;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    /** Record a successful gate scan of {@code ticketId} by {@code scannerUserId}. */
    public static CheckIn record(Long ticketId, Long scannerUserId, String deviceId, Instant now) {
        CheckIn checkIn = new CheckIn();
        checkIn.ticketId = Objects.requireNonNull(ticketId, "ticketId");
        checkIn.checkedInBy = Objects.requireNonNull(scannerUserId, "scannerUserId");
        checkIn.deviceId = deviceId;
        checkIn.status = CheckInStatus.OK;
        checkIn.checkedInAt = Objects.requireNonNull(now, "now");
        return checkIn;
    }

    @PrePersist
    void prePersist() {
        if (checkedInAt == null) checkedInAt = Instant.now();
        if (status == null) status = CheckInStatus.OK;
    }
}
