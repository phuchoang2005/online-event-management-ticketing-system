package com.odoomaster.ticketing.ticketing;

import org.springframework.modulith.NamedInterface;

import java.util.List;

/**
 * Published ticketing API for issuing QR tickets on a paid order.
 *
 * <p>{@code sales}' {@code OrderService} calls this instead of building and saving {@code Ticket}
 * entities itself, so ticket persistence and QR-code generation stay private to the ticketing
 * module. Invoked inside the ordering transaction, so issuance is atomic with the sale.
 *
 * <p>Exposed as the {@code ticketing::issuance} named interface — the only facet {@code sales}
 * may depend on; {@code TicketService}, {@code CheckInService} and {@code ticketing::reporting}
 * stay out of reach.
 */
@NamedInterface("issuance")
public interface TicketIssuance {

    /**
     * Issue one {@code VALID} QR ticket per line of a paid order.
     *
     * @param order the buyer, event, and per-line seat references
     * @return the number of tickets issued
     */
    int issueForOrder(TicketOrder order);

    /** The paid order to issue tickets for. */
    @NamedInterface("issuance")
    record TicketOrder(Long userId, Long eventId, List<TicketLine> lines) {}

    /** One order line: the order item and the seat it holds. */
    @NamedInterface("issuance")
    record TicketLine(Long orderItemId, Long eventSeatId) {}
}
