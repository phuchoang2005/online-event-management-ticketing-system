/**
 * Catalog: events, categories, ticket types, venues/seats/sections and the seat-inventory
 * state machine — the read model behind the storefront and the write model behind admin CRUD.
 *
 * <p>Owns {@code Event}, {@code EventCategory}, {@code TicketType}, {@code EventSeat},
 * {@code Venue}/{@code Seat}/{@code Section} (all in {@code …internal}), the event/seat caches
 * ({@code CacheConfig}) and the {@code SeatLockSweeperJob}.
 *
 * <p>Publishes two named interfaces:
 *
 * <ul>
 *   <li>{@code catalog::events} — {@code EventCatalog} (+ {@code EventSummary},
 *       {@code EventStats}): event lookup, on-sale checks and reporting aggregates.</li>
 *   <li>{@code catalog::inventory} — {@code SeatInventory} (+ {@code SeatDetail}): the
 *       {@code AVAILABLE→LOCKED→SOLD} machine, lock TTL and event-cache eviction.</li>
 * </ul>
 *
 * <p>They are kept apart so a consumer that only reads event metadata ({@code feedback},
 * {@code analytics}) cannot reach the concurrency-critical seat state machine. The rest of the base
 * package — {@code EventService}, {@code AdminEventService}, {@code SeatCatalogService}, the
 * controllers and DTOs — stays in the unnamed interface and is module-private in practice.
 *
 * <p>As the base of the sales chain it depends only on the kernel; the delete-event cascade is
 * fan-out via the {@code shared::contracts} {@code EventDeletedEvent} rather than a compile-time
 * dependency on {@code sales}/{@code ticketing}.
 */
@ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {"shared::errors", "shared::contracts"})
package com.odoomaster.ticketing.catalog;

import org.springframework.modulith.ApplicationModule;
