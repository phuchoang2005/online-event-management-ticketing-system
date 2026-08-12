package com.odoomaster.ticketing.ticketing;

import com.odoomaster.ticketing.catalog.EventCatalog;
import com.odoomaster.ticketing.catalog.EventCatalog.EventSummary;
import com.odoomaster.ticketing.catalog.SeatInventory;
import com.odoomaster.ticketing.catalog.SeatInventory.SeatDetail;
import com.odoomaster.ticketing.ticketing.TicketDtos.ScanRequest;
import com.odoomaster.ticketing.ticketing.TicketDtos.ScanResult;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.ticketing.internal.CheckIn;
import com.odoomaster.ticketing.ticketing.internal.CheckInStatus;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;
import com.odoomaster.ticketing.ticketing.internal.CheckInRepository;
import com.odoomaster.ticketing.ticketing.internal.Ticket;
import com.odoomaster.ticketing.ticketing.internal.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

/**
 * Gate check-in service: validates a ticket's QR code and records a single, idempotent
 * check-in, transitioning the ticket to {@code USED}.
 *
 * <p>Event/seat context for the scan result is read through catalog's {@link EventCatalog} and
 * {@link SeatInventory}, so this module no longer touches the {@code Event}/{@code EventSeat} entities.
 */
@Service
public class CheckInService {

    private final TicketRepository tickets;
    private final CheckInRepository checkIns;
    private final EventCatalog eventCatalog;
    private final SeatInventory seatInventory;

    public CheckInService(TicketRepository tickets, CheckInRepository checkIns,
                          EventCatalog eventCatalog, SeatInventory seatInventory) {
        this.tickets = tickets;
        this.checkIns = checkIns;
        this.eventCatalog = eventCatalog;
        this.seatInventory = seatInventory;
    }

    @Transactional
    public ScanResult scan(Long scannerUserId, ScanRequest req) {
        if (req == null || req.qrCode() == null || req.qrCode().isBlank()) {
            throw new AppException("VALIDATION_FAILED", "qrCode is required.", HttpStatus.BAD_REQUEST);
        }
        Ticket t = tickets.findByQrCode(req.qrCode())
                .orElseThrow(() -> new AppException("TICKET_NOT_FOUND", "Ticket not found.", HttpStatus.NOT_FOUND));

        if (t.getStatus() == TicketStatus.USED || checkIns.existsByTicketId(t.getId())) {
            throw new AppException("ALREADY_USED", "Ticket already checked in.", HttpStatus.CONFLICT);
        }
        if (t.getStatus() != TicketStatus.VALID) {
            throw new AppException("TICKET_NOT_VALID", "Ticket not in VALID state.", HttpStatus.CONFLICT);
        }

        CheckIn ci = CheckIn.record(t.getId(), scannerUserId, req.deviceId(), Instant.now());
        try {
            checkIns.save(ci);
        } catch (DataIntegrityViolationException dup) {
            throw new AppException("ALREADY_USED", "Ticket already checked in.", HttpStatus.CONFLICT);
        }

        t.markUsed();
        tickets.save(t);

        EventSummary ev = eventCatalog.find(t.getEventId()).orElse(null);
        SeatDetail s = seatInventory.findSeats(List.of(t.getEventSeatId())).stream().findFirst().orElse(null);
        return new ScanResult(
                "OK",
                t.getId(),
                t.getEventId(),
                ev != null ? ev.title() : null,
                s != null ? s.rowLabel() : null,
                s != null ? s.seatNumber() : null,
                s != null ? s.section() : null,
                ci.getCheckedInAt());
    }
}
