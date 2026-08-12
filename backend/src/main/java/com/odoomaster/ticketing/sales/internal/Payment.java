package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.sales.payment.PaymentMethod;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * The record of one charge against an order, successful or not.
 *
 * <p>Written for every attempt, including declines — which is what makes the payment funnel in
 * {@code AnalyticsService} capable of reporting anything other than zeros.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_order", columnList = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod provider;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(nullable = false, precision = 14, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Record a gateway outcome. {@code transactionId} is null when the provider declined outright. */
    public static Payment record(Long orderId, PaymentMethod provider, String transactionId,
                                 Money amount, PaymentStatus status, Instant now) {
        Payment payment = new Payment();
        payment.orderId = Objects.requireNonNull(orderId, "orderId");
        payment.provider = Objects.requireNonNull(provider, "provider");
        payment.transactionId = transactionId;
        payment.amount = Objects.requireNonNull(amount, "amount").value();
        payment.status = Objects.requireNonNull(status, "status");
        payment.createdAt = Objects.requireNonNull(now, "now");
        return payment;
    }

    public boolean isSucceeded() {
        return status == PaymentStatus.SUCCEEDED;
    }

    public Money amount() {
        return Money.of(amount);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
