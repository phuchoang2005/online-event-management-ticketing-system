package com.odoomaster.ticketing.shared;

import org.springframework.modulith.NamedInterface;

/**
 * Domain event published by the catalog when an event is deleted.
 *
 * <p>It decouples the cascade: {@code catalog} removes its own rows (seats, categories) and
 * publishes this event; downstream modules ({@code sales}, {@code ticketing}) observe it and
 * purge their own dependent rows (orders/payments, tickets/check-ins). Listeners run
 * synchronously inside the delete transaction so the whole cascade stays atomic.
 *
 * <p>Introduced in Sprint 0 as a published contract; the emitting side and listeners are wired
 * up in Sprint 3.
 *
 * <p>Part of the {@code shared::contracts} named interface.
 *
 * @param eventId id of the deleted event whose dependent rows must be purged
 */
@NamedInterface("contracts")
public record EventDeletedEvent(Long eventId) {
}
