package com.odoomaster.ticketing.ticketing;

import org.springframework.modulith.NamedInterface;

/**
 * Published ticketing API exposing ticket aggregates for reporting.
 *
 * <p>{@code analytics} calls this instead of reaching into ticketing's {@code Ticket} entity or its
 * repository, so the ticket schema stays private to the module.
 *
 * <p>Exposed as the {@code ticketing::reporting} named interface — read-only aggregates, kept
 * separate from {@code ticketing::issuance} so {@code sales} cannot reach reporting and
 * {@code analytics} cannot issue tickets.
 */
@NamedInterface("reporting")
public interface TicketingReporting {

    /** Total number of issued tickets across all events and statuses. */
    long totalTickets();

    /** Number of tickets issued for a single event (any status). */
    long countTicketsForEvent(Long eventId);

    /** Number of tickets in the given status across all events. */
    long countTicketsByStatus(String status);

    /** Number of tickets for a single event in the given status. */
    long countTicketsForEventByStatus(Long eventId, String status);
}
