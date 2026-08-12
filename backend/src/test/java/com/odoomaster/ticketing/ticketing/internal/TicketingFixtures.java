package com.odoomaster.ticketing.ticketing.internal;

import java.time.Instant;

/**
 * Builders for ticketing aggregates in states application code reaches only through a transition.
 * See {@code CatalogFixtures} for why this lives in the production package.
 */
public final class TicketingFixtures {

    private TicketingFixtures() {
    }

    /** A ticket in {@code status} with a persisted id — including states {@code issue()} cannot make. */
    public static Ticket ticket(Long id, TicketStatus status) {
        return ticket(id, status, 4L, 2L, 3L, "qr");
    }

    public static Ticket ticket(Long id, TicketStatus status, Long userId, Long eventId,
                                Long eventSeatId, String qrCode) {
        return new Ticket(id, 5L, userId, eventId, eventSeatId, qrCode, status, Instant.now());
    }

    public static CheckIn checkIn(Long id, Long ticketId, Long scannerUserId, String deviceId) {
        return new CheckIn(id, ticketId, scannerUserId, Instant.now(), CheckInStatus.OK, deviceId);
    }

    /** See {@code SalesFixtures.withId}. */
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
