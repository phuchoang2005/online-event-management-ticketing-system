package com.odoomaster.ticketing.ticketing.internal;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a checkin.
 */
@Entity
@Table(name = "check_ins",
        uniqueConstraints = @UniqueConstraint(name = "uk_check_ins_ticket", columnNames = "ticket_id"),
        indexes = @Index(name = "idx_checkin_at", columnList = "checked_in_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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

    @PrePersist
    void prePersist() {
        if (checkedInAt == null) checkedInAt = Instant.now();
        if (status == null) status = CheckInStatus.OK;
    }
}
