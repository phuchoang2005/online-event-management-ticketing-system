package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.catalog.internal.CacheConfig;
import com.odoomaster.ticketing.catalog.internal.Event;
import com.odoomaster.ticketing.catalog.internal.EventCategory;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.EventStatus;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.catalog.AdminDtos.*;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.catalog.internal.EventCategoryRepository;
import com.odoomaster.ticketing.catalog.internal.EventRepository;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import com.odoomaster.ticketing.catalog.internal.TicketTypeRepository;
import com.odoomaster.ticketing.catalog.internal.TicketType;
import com.odoomaster.ticketing.shared.EventDeletedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Admin/organizer service for the event lifecycle: create, edit, status transitions, and
 * venue section management. Backs the {@code /v1/admin/events/**} endpoints.
 */
@Service
public class AdminEventService {

    private static final Set<EventStatus> ALLOWED_STATUSES = EnumSet.allOf(EventStatus.class);

    private final EventRepository events;
    private final EventCategoryRepository categories;
    private final EventSeatRepository seats;
    private final TicketTypeRepository ticketTypes;
    private final SeatCatalogService catalog;
    private final ApplicationEventPublisher eventPublisher;

    public AdminEventService(EventRepository events, EventCategoryRepository categories,
                             EventSeatRepository seats,
                             TicketTypeRepository ticketTypes,
                             SeatCatalogService catalog,
                             ApplicationEventPublisher eventPublisher) {
        this.events = events;
        this.categories = categories;
        this.seats = seats;
        this.ticketTypes = ticketTypes;
        this.catalog = catalog;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<AdminEventRow> list() {
        return events.findAllForAdmin().stream().map(this::toRow).toList();
    }

    @Transactional(readOnly = true)
    public AdminEventDetail detail(Long id) {
        Event e = events.findById(id)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        List<EventSeat> all = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(id);
        BigDecimal revenue = seats.sumSoldPriceForEvent(id);
        int total = all.size();
        int sold = (int) all.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
        int avail = (int) all.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        return new AdminEventDetail(
                e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getImageUrl(),
                EventService.categoryRefs(e), e.getOrganizer(), e.getStartTime(), e.getEndTime(), e.getStatus().name(),
                total, avail, sold, revenue,
                buildSections(all));
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName()))
                .toList();
    }

    @Transactional
    public CategoryView createCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new AppException("VALIDATION_FAILED", "name is required.", HttpStatus.BAD_REQUEST);
        }
        String trimmed = name.trim();
        EventCategory existing = categories.findByName(trimmed).orElse(null);
        if (existing != null) return new CategoryView(existing.getId(), existing.getName());
        EventCategory saved = categories.save(EventCategory.builder().name(trimmed).build());
        return new CategoryView(saved.getId(), saved.getName());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true)
    })
    public AdminEventDetail create(AdminEventUpsertRequest req) {
        validateTimes(req);
        Event e = Event.builder()
                .title(req.title().trim())
                .description(req.description())
                .location(req.location())
                .organizer(req.organizer())
                .imageUrl(req.imageUrl())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .status(EventStatus.DRAFT)
                .build();
        e.setCategories(resolveCategories(req.categories()));
        events.save(e);
        return detail(e.getId());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#id"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#id")
    })
    public AdminEventDetail update(Long id, AdminEventUpsertRequest req) {
        Event e = events.findById(id)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        validateTimes(req);
        e.setTitle(req.title().trim());
        e.setDescription(req.description());
        e.setLocation(req.location());
        e.setOrganizer(req.organizer());
        e.setImageUrl(req.imageUrl());
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setCategories(resolveCategories(req.categories()));
        events.save(e);
        return detail(e.getId());
    }

    private Set<EventCategory> resolveCategories(List<String> names) {
        Set<EventCategory> out = new HashSet<>();
        if (names == null) return out;
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;
            String n = raw.trim();
            EventCategory c = categories.findByName(n).orElseGet(() ->
                    categories.save(EventCategory.builder().name(n).build()));
            out.add(c);
        }
        return out;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#id"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#id")
    })
    public AdminEventDetail changeStatus(Long id, String status) {
        EventStatus target = EventStatus.parse(status)
                .orElseThrow(() -> new AppException("VALIDATION_FAILED",
                        "Status must be one of " + ALLOWED_STATUSES, HttpStatus.BAD_REQUEST));
        Event e = events.findById(id)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        if (target == EventStatus.PUBLISHED && seats.countByEventId(id) == 0) {
            throw new AppException("EVENT_HAS_NO_SEATS",
                    "Cannot publish an event with no seats.", HttpStatus.CONFLICT);
        }
        if (target == EventStatus.DRAFT && seats.existsByEventIdAndStatus(id, SeatStatus.SOLD)) {
            throw new AppException("EVENT_HAS_TICKETS",
                    "Cannot revert to DRAFT: event already has issued tickets.", HttpStatus.CONFLICT);
        }
        e.setStatus(target);
        events.save(e);
        return detail(e.getId());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true)
    })
    public AdminEventDetail addSection(Long eventId, SectionUpsertRequest req) {
        Event e = events.findById(eventId)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        String name = req.name().trim();
        boolean exists = seats.findByEventIdAndSection(eventId, name).stream().findAny().isPresent();
        if (exists) {
            throw new AppException("SECTION_EXISTS",
                    "Section '" + name + "' already exists for this event.", HttpStatus.CONFLICT);
        }
        char startLetter = nextStartingLetter(eventId);
        var venue = catalog.ensureVenue(e.getLocation(), null);
        var section = catalog.ensureSection(venue.getId(), name);
        int quantity = req.rows() * req.seatsPerRow();
        TicketType tt = ticketTypes.findByEventIdAndName(eventId, name).orElse(null);
        if (tt == null) {
            tt = ticketTypes.save(TicketType.builder()
                    .eventId(eventId)
                    .name(name)
                    .price(req.price())
                    .quantity(quantity)
                    .soldQuantity(0)
                    .build());
        } else {
            tt.setPrice(req.price());
            tt.setQuantity(tt.getQuantity() + quantity);
            ticketTypes.save(tt);
        }
        List<EventSeat> toSave = new ArrayList<>();
        for (int r = 0; r < req.rows(); r++) {
            char rowLabel = (char) (startLetter + r);
            for (int n = 1; n <= req.seatsPerRow(); n++) {
                String rl = String.valueOf(rowLabel);
                String sn = String.format("%02d", n);
                var seat = catalog.ensureSeat(section.getId(), rl, sn);
                toSave.add(EventSeat.create(e.getId(), seat.getId(), tt.getId(),
                        name, rl, sn, req.price()));
            }
        }
        seats.saveAll(toSave);
        return detail(eventId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true)
    })
    public AdminEventDetail updateSection(Long eventId, String section, SectionUpdateRequest req) {
        List<EventSeat> current = seats.findByEventIdAndSection(eventId, section);
        if (current.isEmpty()) {
            throw new AppException("SECTION_NOT_FOUND",
                    "Section not found: " + section, HttpStatus.NOT_FOUND);
        }
        String newName = req.name().trim();
        // Reject the whole request up front rather than repricing half a section and then throwing:
        // a sold seat's price is realised revenue (sumSoldPriceForEvent), so it is immutable.
        boolean repricing = current.stream().anyMatch(s -> s.getPrice().compareTo(req.price()) != 0);
        if (repricing && current.stream().anyMatch(EventSeat::isSold)) {
            throw new AppException("SEAT_SOLD_IMMUTABLE",
                    "Cannot change the price of section " + section + ": it already has sold seats.",
                    HttpStatus.CONFLICT);
        }
        for (EventSeat s : current) {
            s.relabelSection(newName);
            // Only when the price actually moves: a no-op reprice on a sold seat is still a reprice,
            // and renaming a section that happens to contain sold seats must stay allowed.
            if (repricing) {
                s.reprice(req.price());
            }
        }
        seats.saveAll(current);
        return detail(eventId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#id"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#id"),
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true)
    })
    public void delete(Long id) {
        Event e = events.findById(id)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        if (e.getStatus() == EventStatus.PUBLISHED) {
            throw new AppException("EVENT_PUBLISHED_NOT_DELETABLE",
                    "Cannot delete a PUBLISHED event. Cancel or complete it first.",
                    HttpStatus.CONFLICT);
        }
        // Cascade via a published contract: downstream modules purge their own dependent rows
        // (ticketing: check-ins + tickets; sales: order items, payments, orders) synchronously in this
        // transaction, then catalog removes its own seats and the event. Keeps the cascade atomic
        // without catalog reaching into sales/ticketing repositories.
        eventPublisher.publishEvent(new EventDeletedEvent(id));
        seats.deleteAll(seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(id));
        events.delete(e);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENT_DETAIL, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENT_SEATS, key = "#eventId"),
            @CacheEvict(value = CacheConfig.EVENTS_LIST, allEntries = true)
    })
    public AdminEventDetail deleteSection(Long eventId, String section) {
        List<EventSeat> current = seats.findByEventIdAndSection(eventId, section);
        if (current.isEmpty()) {
            throw new AppException("SECTION_NOT_FOUND",
                    "Section not found: " + section, HttpStatus.NOT_FOUND);
        }
        boolean hasSold = current.stream().anyMatch(s -> s.getStatus() == SeatStatus.SOLD || s.getStatus() == SeatStatus.LOCKED);
        if (hasSold) {
            throw new AppException("SECTION_IN_USE",
                    "Cannot delete a section with sold or locked seats.", HttpStatus.CONFLICT);
        }
        seats.deleteAll(current);
        return detail(eventId);
    }

    private void validateTimes(AdminEventUpsertRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new AppException("VALIDATION_FAILED",
                    "endTime must be after startTime.", HttpStatus.BAD_REQUEST);
        }
    }

    private char nextStartingLetter(Long eventId) {
        List<EventSeat> all = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId);
        char next = 'A';
        for (EventSeat s : all) {
            if (s.getRowLabel() != null && !s.getRowLabel().isEmpty()) {
                char c = s.getRowLabel().charAt(0);
                if (c >= next) next = (char) (c + 1);
            }
        }
        return next;
    }

    private AdminEventRow toRow(Event e) {
        List<EventSeat> all = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(e.getId());
        int total = all.size();
        int sold = (int) all.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
        int avail = (int) all.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        BigDecimal revenue = seats.sumSoldPriceForEvent(e.getId());
        return new AdminEventRow(
                e.getId(), e.getTitle(), e.getLocation(), EventService.categoryRefs(e), e.getOrganizer(),
                e.getStatus().name(), e.getStartTime(), e.getEndTime(), e.getCreatedAt(),
                total, avail, sold, revenue);
    }

    private List<SectionSummary> buildSections(List<EventSeat> all) {
        Map<String, List<EventSeat>> grouped = new LinkedHashMap<>();
        for (EventSeat s : all) {
            grouped.computeIfAbsent(s.getSection(), k -> new ArrayList<>()).add(s);
        }
        List<SectionSummary> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<EventSeat> list = entry.getValue();
            Set<String> rows = new HashSet<>();
            int sold = 0, avail = 0;
            BigDecimal price = list.get(0).getPrice();
            for (EventSeat s : list) {
                rows.add(s.getRowLabel());
                if (s.getStatus() == SeatStatus.SOLD) sold++;
                else if (s.getStatus() == SeatStatus.AVAILABLE) avail++;
            }
            out.add(new SectionSummary(entry.getKey(), price, rows.size(), list.size(), avail, sold));
        }
        return out;
    }
}
