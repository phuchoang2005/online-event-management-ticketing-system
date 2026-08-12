package com.odoomaster.ticketing.ticketing.internal;

import com.odoomaster.ticketing.ticketing.TicketIssuance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

/**
 * Ticketing-owned implementation of {@link TicketIssuance}. Builds and persists
 * one {@code VALID}
 * ticket per order line with a fresh random QR code. {@code @Transactional} so
 * it joins the caller's
 * ordering transaction — issuance commits atomically with the sale.
 */
@Service
public class TicketIssuanceImpl implements TicketIssuance {

  private final TicketRepository tickets;

  public TicketIssuanceImpl(TicketRepository tickets) {
    this.tickets = tickets;
  }

  @Override
  @Transactional
  public int issueForOrder(TicketOrder order) {
    int issued = 0;
    for (TicketLine line : order.lines()) {
      tickets.save(Ticket.issue(line.orderItemId(), order.userId(), order.eventId(),
          line.eventSeatId(), QrCode.generate(), Instant.now()));
      issued++;
    }
    return issued;
  }
}
