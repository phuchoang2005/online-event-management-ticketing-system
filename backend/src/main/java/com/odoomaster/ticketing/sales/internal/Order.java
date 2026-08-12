package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.sales.payment.PaymentMethod;
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
 * A customer's purchase, and the aggregate root of the {@code PENDING → PAID | CANCELLED} state
 * machine.
 *
 * <p>Before ADR-0013 those transitions were {@code if/else} chains on strings in
 * {@code OrderService}, so "can this order be paid?" was answered in three places and nothing
 * stopped a caller from writing {@code setStatus("PAID")} without a payment.
 *
 * <p><strong>Idempotency lives in two places, on purpose.</strong> {@link #pay} tolerates an
 * already-paid order so re-entering the aggregate is safe, but {@code OrderService.pay()} still
 * returns early on {@link #isPaid()} before doing anything. The aggregate cannot suppress the
 * service's side effects — marking seats SOLD, charging the gateway, issuing tickets — so the guard
 * has to exist at both levels. A duplicate payment request must not mint a second set of tickets.
 */
@Entity
@Table(name = "orders",
        indexes = {
            @Index(name = "idx_orders_user", columnList = "user_id"),
            @Index(name = "idx_orders_status", columnList = "status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 0)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** A new PENDING order for {@code total}, with its seats already held. */
    public static Order place(Long userId, Long eventId, Money total, Instant now) {
        Order order = new Order();
        order.userId = Objects.requireNonNull(userId, "userId");
        order.eventId = Objects.requireNonNull(eventId, "eventId");
        order.totalAmount = Objects.requireNonNull(total, "total").value();
        order.status = OrderStatus.PENDING;
        order.createdAt = Objects.requireNonNull(now, "now");
        return order;
    }

    // ── queries ─────────────────────────────────────────────────────────────────────────

    public Money total() {
        return Money.of(totalAmount);
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    public boolean isPaid() {
        return status == OrderStatus.PAID;
    }

    public boolean isPayable() {
        return status == OrderStatus.PENDING;
    }

    // ── transitions ─────────────────────────────────────────────────────────────────────

    /**
     * Settle the order.
     *
     * <p>A no-op when already paid, so a retried request cannot overwrite the original payment
     * method or timestamp.
     *
     * @throws DomainException {@code ORDER_STATE_INVALID} if the order is cancelled or refunded
     */
    public void pay(PaymentMethod method, Instant paidAt) {
        if (isPaid()) {
            return;
        }
        if (!isPayable()) {
            throw new DomainException("ORDER_STATE_INVALID", "Order cannot be paid in state " + status);
        }
        this.status = OrderStatus.PAID;
        this.paymentMethod = Objects.requireNonNull(method, "paymentMethod");
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    }

    /**
     * Abandon the order before payment. A no-op if already cancelled, so a double-cancel is safe.
     *
     * @throws DomainException {@code ORDER_ALREADY_PAID} if the order has been settled — refunding a
     *                         paid order is a different operation with different consequences
     */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        if (isPaid()) {
            throw new DomainException("ORDER_ALREADY_PAID", "Cannot cancel a paid order.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = OrderStatus.PENDING;
    }
}
