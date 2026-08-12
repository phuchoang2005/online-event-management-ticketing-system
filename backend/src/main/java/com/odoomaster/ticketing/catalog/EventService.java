package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.catalog.internal.Event;
import com.odoomaster.ticketing.catalog.internal.EventCategory;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.EventStatus;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.catalog.EventDtos.*;
import com.odoomaster.ticketing.catalog.internal.CacheConfig;
import com.odoomaster.ticketing.shared.AppException;
import com.odoomaster.ticketing.catalog.internal.EventRepository;
import com.odoomaster.ticketing.catalog.internal.EventSeatRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;

/**
 * Public, read-only event service: paginated listings, trending, event detail, and the seat
 * map. Reads are cached (see {@link com.odoomaster.ticketing.catalog.internal.CacheConfig}) for the browse path.
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository events;
    private final EventSeatRepository seats;
    private final Clock clock;

    public EventService(EventRepository events, EventSeatRepository seats, Clock clock) {
        this.events = events;
        this.seats = seats;
        this.clock = clock;
    }

    public List<EventSummary> listTrending(int limit) {
        int safeLimit = Math.min(20, Math.max(1, limit));
        return events.findTrending(EventStatus.PUBLISHED, clock.instant(), PageRequest.of(0, safeLimit))
                .stream().map(this::toSummary).toList();
    }

    public EventPage listPaged(int page, int limit, String category, String q) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        String nCat = (category == null || category.isBlank()) ? null : category;
        String nQ = (q == null || q.isBlank()) ? null : q.trim();
        Page<Event> result = events.findPublished(EventStatus.PUBLISHED, nCat, nQ,
                PageRequest.of(safePage - 1, safeLimit));
        List<EventSummary> items = result.getContent().stream().map(this::toSummary).toList();
        boolean hasMore = result.hasNext();
        return new EventPage(items, new PageMeta(safePage, safeLimit, result.getTotalElements(), hasMore));
    }

    static List<CategoryRef> categoryRefs(Event e) {
        if (e.getCategories() == null) return List.of();
        return e.getCategories().stream()
                .sorted(Comparator.comparing(EventCategory::getName))
                .map(c -> new CategoryRef(c.getId(), c.getName()))
                .toList();
    }

    private EventSummary toSummary(Event e) {
        var s = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(e.getId());
        BigDecimal min = s.stream().map(EventSeat::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        int avail = (int) s.stream().filter(x -> x.getStatus() == SeatStatus.AVAILABLE).count();
        return new EventSummary(e.getId(), e.getTitle(), e.getLocation(), e.getImageUrl(),
                categoryRefs(e), e.getOrganizer(),
                e.getStartTime(), e.getEndTime(), e.getStatus().name(), min, avail, s.size());
    }

    @Cacheable(CacheConfig.EVENTS_LIST)
    public List<EventSummary> list() {
        return events.findAllByStatusOrderByStartTimeAsc(EventStatus.PUBLISHED).stream()
                .map(this::toSummary)
                .toList();
    }

    @Cacheable(value = CacheConfig.EVENT_DETAIL, key = "#id")
    public EventDetail detail(Long id) {
        Event e = events.findById(id)
                .orElseThrow(() -> new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND));
        var s = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(e.getId());
        BigDecimal min = s.stream().map(EventSeat::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = s.stream().map(EventSeat::getPrice).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        int avail = (int) s.stream().filter(x -> x.getStatus() == SeatStatus.AVAILABLE).count();
        return new EventDetail(e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getImageUrl(),
                categoryRefs(e), e.getOrganizer(),
                e.getStartTime(), e.getEndTime(), e.getStatus().name(), min, max, avail, s.size());
    }

    @Cacheable(value = CacheConfig.EVENT_SEATS, key = "#eventId")
    public SeatMap seats(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new AppException("EVENT_NOT_FOUND", "Event not found.", HttpStatus.NOT_FOUND);
        }
        var list = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId).stream()
                .map(s -> new SeatItem(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getSection(),
                        s.getPrice(), s.getStatus().name(), s.lockedUntil()))
                .toList();
        return new SeatMap(eventId, list);
    }
}
