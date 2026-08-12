package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import java.util.Objects;
import com.odoomaster.ticketing.shared.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.math.BigDecimal;

/**
 * JPA entity mapping the persistence row for a tickettype.
 */
@Entity
@Table(name = "ticket_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_tickettype_event_name",
                columnNames = {"event_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    /** A priced tier for an event, with an initial allocation. */
    public static TicketType create(Long eventId, String name, BigDecimal price, int quantity) {
        TicketType type = new TicketType();
        type.eventId = Objects.requireNonNull(eventId, "eventId");
        type.name = Objects.requireNonNull(name, "name");
        type.price = Objects.requireNonNull(price, "price");
        type.quantity = quantity;
        type.soldQuantity = 0;
        return type;
    }

    /** Extend the allocation when more seats are added to the tier's section. */
    public void addCapacity(int extra) {
        if (extra < 0) {
            throw new DomainException("VALIDATION_FAILED", "Cannot remove capacity by adding a negative amount.");
        }
        this.quantity = (quantity == null ? 0 : quantity) + extra;
    }

    public void reprice(BigDecimal newPrice) {
        this.price = Objects.requireNonNull(newPrice, "price");
    }

    @PrePersist
    void prePersist() {
        if (quantity == null) quantity = 0;
        if (soldQuantity == null) soldQuantity = 0;
    }
}
