/**
 * Ticketing: issued tickets and gate check-in.
 *
 * <p>Owns {@code Ticket} and {@code CheckIn} (in {@code …internal}), the {@code TicketService}
 * (QR issuance / retrieval) and {@code CheckInService} (gate validation).
 *
 * <p>Publishes two named interfaces:
 *
 * <ul>
 *   <li>{@code ticketing::issuance} — {@code TicketIssuance} (+ {@code TicketOrder},
 *       {@code TicketLine}): issue tickets for a paid order, called by {@code sales} inside the
 *       single pay transaction.</li>
 *   <li>{@code ticketing::reporting} — {@code TicketingReporting}: ticket/check-in aggregates for
 *       {@code analytics}.</li>
 * </ul>
 *
 * <p>Splitting them means {@code sales} can issue but not report, and {@code analytics} can report
 * but not issue. {@code TicketService}, {@code CheckInService} and {@code TicketController} are
 * published to neither.
 *
 * <p>Depends on {@code catalog}'s event and inventory facets plus the kernel; purges its rows on the
 * {@code shared::contracts} {@code EventDeletedEvent} cascade.
 */
@ApplicationModule(
        displayName = "Ticketing",
        allowedDependencies = {
                "catalog::events",
                "catalog::inventory",
                "shared::errors",
                "shared::security",
                "shared::contracts"})
package com.odoomaster.ticketing.ticketing;

import org.springframework.modulith.ApplicationModule;
