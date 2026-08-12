package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.catalog.internal.TicketType;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.catalog.internal.EventRepository;
import com.odoomaster.ticketing.catalog.internal.TicketTypeRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller for admin ticket-type management under {@code /v1/admin}.
 */
@RestController
@RequestMapping("/v1/admin/events/{eventId}/ticket-types")
public class AdminTicketTypeController {

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;

    public AdminTicketTypeController(EventRepository events, TicketTypeRepository ticketTypes) {
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    public record TicketTypeView(Long id, Long eventId, String name, BigDecimal price,
                                 Integer quantity, Integer soldQuantity) {}

    public record CreateRequest(@NotBlank @Size(max = 64) String name,
                                @NotNull @Positive BigDecimal price,
                                @NotNull @Positive Integer quantity) {}

    @GetMapping
    public List<TicketTypeView> list(@PathVariable Long eventId) {
        ensureEvent(eventId);
        return ticketTypes.findByEventIdOrderByPriceAsc(eventId).stream()
                .map(this::view).toList();
    }

    @PostMapping
    public TicketTypeView create(@PathVariable Long eventId, @RequestBody CreateRequest req) {
        ensureEvent(eventId);
        if (ticketTypes.findByEventIdAndName(eventId, req.name().trim()).isPresent()) {
            throw new AppException("TICKET_TYPE_EXISTS",
                    "A ticket type with this name already exists for this event.", HttpStatus.CONFLICT);
        }
        TicketType tt = ticketTypes.save(TicketType.create(eventId, req.name().trim(),
                req.price(), req.quantity()));
        return view(tt);
    }

    private TicketTypeView view(TicketType t) {
        return new TicketTypeView(t.getId(), t.getEventId(), t.getName(), t.getPrice(),
                t.getQuantity(), t.getSoldQuantity());
    }

    private void ensureEvent(Long id) {
        if (!events.existsById(id)) {
            throw new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND);
        }
    }
}
