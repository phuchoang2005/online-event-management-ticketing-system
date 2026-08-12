package com.odoomaster.ticketing.sales.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One seat on an order, priced at the moment of purchase.
 *
 * <p>Immutable after creation by design: the price recorded here is what the buyer agreed to, so a
 * later change to the seat's list price must not rewrite it. That is why there is a factory and no
 * setters.
 */
@Entity
@Table(name = "order_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_items_seat", columnNames = "event_seat_id"),
        indexes = @Index(name = "idx_order_items_order", columnList = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_seat_id", nullable = false)
    private Long eventSeatId;

    @Column(name = "ticket_type_id")
    private Long ticketTypeId;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    /** Record {@code seatId} on {@code orderId} at the price the buyer was quoted. */
    public static OrderItem forSeat(Long orderId, Long eventSeatId, Long ticketTypeId, Money price) {
        OrderItem item = new OrderItem();
        item.orderId = Objects.requireNonNull(orderId, "orderId");
        item.eventSeatId = Objects.requireNonNull(eventSeatId, "eventSeatId");
        item.ticketTypeId = ticketTypeId;
        item.price = Objects.requireNonNull(price, "price").value();
        return item;
    }

    /** The agreed price as money rather than a bare number. */
    public Money amount() {
        return Money.of(price);
    }
}
