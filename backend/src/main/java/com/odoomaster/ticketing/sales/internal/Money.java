package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.shared.DomainException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/**
 * A non-negative amount of money.
 *
 * <p>Gives the arithmetic a home. Order totals were computed as a bare
 * {@code reduce(BigDecimal.ZERO, BigDecimal::add)} inline in {@code OrderService}, with nothing
 * stopping a negative total from being persisted and nothing naming what the number meant.
 *
 * <p><strong>Deliberately not persisted.</strong> As an {@code @Embeddable} it would rename
 * {@code total_amount} to {@code total_amount_value} without an {@code @AttributeOverride}, and it
 * would break the four {@code SUM(o.totalAmount)} aggregate queries in {@link OrderRepository};
 * Hibernate 6's aggregate functions over converted basic types are likewise unreliable. The columns
 * stay {@code BigDecimal} and the entities expose {@code total()} / {@code amount()} alongside the
 * raw getters that DTO mapping uses. It is the arithmetic that needed a type, not the column.
 *
 * <p>Currency-free on purpose: this system prices exclusively in VND, and inventing a currency field
 * that is always the same value would be ceremony rather than modelling.
 *
 * @param value the amount, never null and never negative
 */
public record Money(BigDecimal value) {

    public Money {
        Objects.requireNonNull(value, "amount");
        if (value.signum() < 0) {
            throw new DomainException("VALIDATION_FAILED", "Amount cannot be negative: " + value);
        }
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    /** Total a collection of amounts, e.g. the seat prices making up an order. Empty sums to zero. */
    public static Money sum(Collection<BigDecimal> parts) {
        return new Money(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    /**
     * Value equality by amount, ignoring scale — {@code 100} and {@code 100.00} are the same money.
     * {@code BigDecimal.equals} disagrees, which is exactly the trap this wrapper hides.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
