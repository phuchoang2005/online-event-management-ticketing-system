package com.odoomaster.ticketing.catalog;

import com.odoomaster.ticketing.catalog.internal.Seat;
import com.odoomaster.ticketing.catalog.internal.Section;
import com.odoomaster.ticketing.catalog.internal.Venue;
import com.odoomaster.ticketing.catalog.internal.SeatRepository;
import com.odoomaster.ticketing.catalog.internal.SectionRepository;
import com.odoomaster.ticketing.catalog.internal.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lazily ensures the venue/section/seat catalog rows exist for an event, creating them on demand.
 */
@Service
public class SeatCatalogService {

    private final VenueRepository venues;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public SeatCatalogService(VenueRepository venues, SectionRepository sections, SeatRepository seats) {
        this.venues = venues;
        this.sections = sections;
        this.seats = seats;
    }

    @Transactional
    public Venue ensureVenue(String name, String address) {
        String key = (name == null || name.isBlank()) ? "Unknown" : name.trim();
        return venues.findByName(key).orElseGet(() ->
                venues.save(Venue.named(key, address)));
    }

    @Transactional
    public Section ensureSection(Long venueId, String name) {
        String key = (name == null || name.isBlank()) ? "Default" : name.trim();
        return sections.findByVenueIdAndName(venueId, key).orElseGet(() ->
                sections.save(Section.of(venueId, key)));
    }

    @Transactional
    public Seat ensureSeat(Long sectionId, String rowLabel, String seatNumber) {
        return seats.findBySectionIdAndRowLabelAndSeatNumber(sectionId, rowLabel, seatNumber).orElseGet(() ->
                seats.save(Seat.of(sectionId, rowLabel, seatNumber)));
    }

    @Transactional
    public Long resolveSeatId(String venueName, String sectionName, String rowLabel, String seatNumber) {
        Venue v = ensureVenue(venueName, null);
        Section s = ensureSection(v.getId(), sectionName);
        Seat seat = ensureSeat(s.getId(), rowLabel, seatNumber);
        return seat.getId();
    }
}
