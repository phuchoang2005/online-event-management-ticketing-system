package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

/**
 * JPA entity mapping the persistence row for a section.
 */
@Entity
@Table(name = "sections",
        uniqueConstraints = @UniqueConstraint(name = "uk_section_venue_name", columnNames = {"venue_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(nullable = false, length = 64)
    private String name;

    /** Reference data: a factory, no lifecycle and nothing to transition. */
    public static Section of(Long venueId, String name) {
        Section section = new Section();
        section.venueId = Objects.requireNonNull(venueId, "venueId");
        section.name = Objects.requireNonNull(name, "name");
        return section;
    }
}
