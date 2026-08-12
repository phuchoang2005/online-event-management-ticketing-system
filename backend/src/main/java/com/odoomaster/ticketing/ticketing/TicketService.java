package com.odoomaster.ticketing.ticketing;

import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.catalog.SeatInventory.SeatDetail;
import com.odoomaster.ticketing.ticketing.TicketDtos.TicketPage;
import com.odoomaster.ticketing.ticketing.TicketDtos.TicketPageMeta;
import com.odoomaster.ticketing.ticketing.TicketDtos.TicketView;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.ticketing.internal.Ticket;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;
import com.odoomaster.ticketing.ticketing.internal.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

/**
 * Read/cancel service for a user's purchased tickets, including QR ticket detail.
 *
 * <p>Event/seat context is read through catalog's {@link EventCatalog}/{@link SeatInventory}, and
 * cancelling a ticket frees its seat via {@link SeatInventory#releaseSold} — so this module no longer
 * touches the {@code Event}/{@code EventSeat} entities.
 */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository tickets;
    private final EventCatalog eventCatalog;
    private final SeatInventory seatInventory;

    public TicketService(TicketRepository tickets, EventCatalog eventCatalog, SeatInventory seatInventory) {
        this.tickets = tickets;
        this.eventCatalog = eventCatalog;
        this.seatInventory = seatInventory;
    }

    public List<TicketView> listMine(Long userId) {
        return tickets.findByUserIdOrderByIssuedAtDesc(userId).stream().map(this::view).toList();
    }

    private static final Set<TicketStatus> TICKET_STATUSES = EnumSet.allOf(TicketStatus.class);

    public TicketPage listMinePaged(Long userId, int page, int limit, String status) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(50, Math.max(1, limit));
        PageRequest pr = PageRequest.of(safePage - 1, safeLimit);
        Page<Ticket> result;
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            result = tickets.findByUserIdOrderByIssuedAtDesc(userId, pr);
        } else {
            TicketStatus s = TicketStatus.parse(status)
                    .orElseThrow(() -> new AppException("VALIDATION_FAILED",
                            "status must be one of " + TICKET_STATUSES + " or omitted.",
                            HttpStatus.BAD_REQUEST));
            result = tickets.findByUserIdAndStatusOrderByIssuedAtDesc(userId, s, pr);
        }
        List<TicketView> data = result.getContent().stream().map(this::view).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("all", tickets.countByUserId(userId));
        for (TicketStatus s : TICKET_STATUSES) {
            counts.put(s.name(), tickets.countByUserIdAndStatus(userId, s));
        }
        return new TicketPage(data,
                new TicketPageMeta(safePage, safeLimit, result.getTotalElements(), result.hasNext()),
                counts);
    }

    public TicketView getMine(Long userId, Long ticketId) {
        Ticket t = tickets.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new AppException("TICKET_NOT_FOUND", "Ticket not found.", HttpStatus.NOT_FOUND));
        return view(t);
    }

    @Transactional
    public void cancelMine(Long userId, Long ticketId) {
        Ticket t = tickets.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new AppException("TICKET_NOT_FOUND", "Ticket not found.", HttpStatus.NOT_FOUND));
        if (t.getStatus() == TicketStatus.USED) {
            throw new AppException("TICKET_ALREADY_USED",
                    "Cannot delete a ticket that has already been checked in.",
                    HttpStatus.CONFLICT);
        }
        if (t.getStatus() == TicketStatus.CANCELLED) {
            return;
        }
        t.cancel();
        tickets.save(t);
        // Free the seat for resale: catalog transitions it SOLD -> AVAILABLE and evicts the caches.
        seatInventory.releaseSold(t.getEventId(), List.of(t.getEventSeatId()));
    }

    private TicketView view(Ticket t) {
        EventSummary ev = eventCatalog.find(t.getEventId()).orElse(null);
        SeatDetail s = seatInventory.findSeats(List.of(t.getEventSeatId())).stream().findFirst().orElse(null);
        return new TicketView(t.getId(), t.getQrCode(), t.getStatus().name(),
                t.getEventId(),
                ev != null ? ev.title() : null,
                ev != null ? ev.location() : null,
                ev != null ? ev.startTime() : null,
                s != null ? s.rowLabel() : null,
                s != null ? s.seatNumber() : null,
                s != null ? s.section() : null,
                s != null ? s.price() : null,
                t.getIssuedAt());
    }
}
