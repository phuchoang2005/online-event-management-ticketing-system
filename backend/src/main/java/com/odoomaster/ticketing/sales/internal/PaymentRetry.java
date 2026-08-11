package com.odoomaster.ticketing.sales.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * One numbered retry attempt against a {@link Payment}, with the gateway's decline reason.
 *
 * <p>Dead infrastructure until ADR-0013: the table, entity and service existed but
 * {@code PaymentRetryService.recordAttempt} had no callers, because {@code OrderService} never read
 * {@code PaymentResult.success()} and therefore had no failure branch at all.
 */
@Entity
@Table(name = "payment_retries",
        indexes = @Index(name = "idx_retry_payment", columnList = "payment_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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

    /** Record attempt number {@code attemptNo} against {@code paymentId}. */
    public static PaymentRetry attempt(Long paymentId, PaymentRetryStatus status, int attemptNo,
                                       String errorCode, Instant now) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("Attempt numbers start at 1, was: " + attemptNo);
        }
        PaymentRetry retry = new PaymentRetry();
        retry.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        retry.status = Objects.requireNonNull(status, "status");
        retry.attemptNo = attemptNo;
        retry.errorCode = errorCode;
        retry.attemptedAt = Objects.requireNonNull(now, "now");
        return retry;
    }

    /**
     * The next attempt number given how many already exist.
     *
     * <p>Read-modify-write, so it is only correct under the row lock the surrounding transaction
     * provides — two concurrent retries on the same payment outside one can still collide.
     */
    public static int nextAttemptNo(long existingCount) {
        return (int) existingCount + 1;
    }
}
