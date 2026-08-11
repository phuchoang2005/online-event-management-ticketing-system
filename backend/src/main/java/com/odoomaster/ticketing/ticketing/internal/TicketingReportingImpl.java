package com.odoomaster.ticketing.ticketing.internal;

import com.odoomaster.ticketing.ticketing.TicketingReporting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ticketing-owned implementation of {@link TicketingReporting}. Wraps
 * {@link TicketRepository} count
 * queries so callers never touch the {@code Ticket} entity.
 */
@Service
@Transactional(readOnly = true)
public class TicketingReportingImpl implements TicketingReporting {

  private final TicketRepository tickets;

  public TicketingReportingImpl(TicketRepository tickets) {
    this.tickets = tickets;
  }

  @Override
  public long totalTickets() {
    return tickets.count();
  }

  @Override
  public long countTicketsForEvent(Long eventId) {
    return tickets.countByEventId(eventId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Anti-corruption boundary: an unrecognised status answers {@code 0} rather than throwing.
   */
  @Override
  public long countTicketsByStatus(String status) {
    return TicketStatus.parse(status).map(tickets::countByStatus).orElse(0L);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Anti-corruption boundary: an unrecognised status answers {@code 0} rather than throwing.
   */
  @Override
  public long countTicketsForEventByStatus(Long eventId, String status) {
    return TicketStatus.parse(status)
        .map(s -> tickets.countByEventIdAndStatus(eventId, s))
        .orElse(0L);
  }
}
