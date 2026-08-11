package com.odoomaster.ticketing.sales.internal;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity mapping the persistence row for a paymentretry.
 */
@Entity
@Table(name = "payment_retries",
        indexes = @Index(name = "idx_retry_payment", columnList = "payment_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRetryStatus status;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    void prePersist() {
        if (attemptedAt == null) attemptedAt = Instant.now();
    }
}
