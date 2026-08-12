package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.sales.payment.PaymentMethod;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Builders for sales aggregates in states application code can only reach through a transition.
 *
 * <p>See {@code CatalogFixtures} for why this lives in the production package: the aggregates have
 * no setters since ADR-0013, and their all-args constructors are package-private, which is the only
 * way to fake a persisted {@code id} or an order that was already paid. {@code ModularityTests}
 * analyses {@code src/main} only, so nothing here widens a module boundary.
 */
public final class SalesFixtures {

    private SalesFixtures() {
    }

    /** An order in {@code status} with a persisted id, owned by {@code userId}. */
    public static Order order(Long id, OrderStatus status, Long userId) {
        return order(id, status, userId, 1L, BigDecimal.TEN);
    }

    public static Order order(Long id, OrderStatus status, Long userId, Long eventId, BigDecimal total) {
        PaymentMethod method = status == OrderStatus.PAID ? PaymentMethod.MOCK : null;
        Instant paidAt = status == OrderStatus.PAID ? Instant.now() : null;
        return new Order(id, userId, eventId, total, status, method, Instant.now(), paidAt);
    }

    public static OrderItem orderItem(Long id, Long orderId, Long eventSeatId, BigDecimal price) {
        return new OrderItem(id, orderId, eventSeatId, 3L, price);
    }

    public static Payment payment(Long id, Long orderId, PaymentStatus status, BigDecimal amount) {
        return new Payment(id, orderId, PaymentMethod.MOCK, "TXN-" + id, amount, status, Instant.now());
    }

    public static PaymentRetry paymentRetry(Long id, Long paymentId, PaymentRetryStatus status, int attemptNo) {
        return new PaymentRetry(id, paymentId, status, attemptNo, "E" + attemptNo, Instant.now());
    }

    /**
     * Stamp a generated id onto an already-constructed aggregate, the way Hibernate does on
     * {@code save()} for an {@code IDENTITY} column.
     *
     * <p>Used by tests that stub {@code repository.save(...)} to mimic the database assigning a key
     * to the very instance the service is still holding. Reflection rather than a setter on purpose:
     * identity assignment is the persistence layer's job, and adding a production-visible
     * {@code setId} to satisfy a mock would reopen the aggregate for exactly the kind of arbitrary
     * mutation ADR-0013 closed off.
     */
    public static <T> T withId(T entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign id to " + entity.getClass().getSimpleName(), e);
        }
    }
}
