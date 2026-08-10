package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.shared.AppException;

import org.springframework.modulith.NamedInterface;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Published catalog API for reading event information across module boundaries.
 *
 * <p>Consumers ({@code sales}, {@code ticketing}, {@code analytics}, {@code feedback}) call this
 * instead of reaching into catalog's {@code Event} entity or its repository, so the event schema
 * stays private to the module. Returns the lightweight {@link EventSummary} projection rather than
 * the JPA entity.
 *
 * <p>Exposed as the {@code catalog::events} named interface; consumers declare
 * {@code allowedDependencies = "catalog::events"}, which leaves the rest of catalog's base package
 * ({@code EventService}, {@code AdminEventService}, the controllers and DTOs) unreachable.
 */
@NamedInterface("events")
public interface EventCatalog {

    /**
     * Validate the event exists and is currently on sale, returning its summary.
     *
     * @param eventId the event to check
     * @return the event summary
     * @throws AppException {@code EVENT_NOT_FOUND} if missing, {@code EVENT_NOT_PUBLISHED} if not
     *                      in {@code PUBLISHED} status
     */
    EventSummary requireOnSale(Long eventId);

    /**
     * Look up an event summary for rendering (e.g. order/ticket views), or empty if it does not exist.
     *
     * @param eventId the event to read
     * @return the summary, or {@link Optional#empty()} if not found
     */
    Optional<EventSummary> find(Long eventId);

    /**
     * List every event (newest first) with the fields admin reporting needs: identity, status, and
     * category names. Lets {@code analytics} build its leaderboard/category breakdown without touching
     * the {@code Event} entity.
     *
     * @return one {@link EventStats} per event
     */
    List<EventStats> listForReporting();

    /** Total number of events across all statuses. */
    long countEvents();

    /** Number of events in the given lifecycle status (e.g. {@code PUBLISHED}). */
    long countEventsByStatus(String status);

    /** Total number of seats across all events. */
    long countAllSeats();

    /** Number of {@code SOLD} seats across all events. */
    long countSoldSeats();

    /**
     * Immutable projection of an event exposed across module boundaries.
     */
    @NamedInterface("events")
    record EventSummary(Long id, String title, String location,
                        Instant startTime, Instant endTime, String status) {}

    /**
     * Reporting projection of an event: identity, status, and its category names.
     */
    @NamedInterface("events")
    record EventStats(Long id, String title, String status, Set<String> categoryNames) {}
}
