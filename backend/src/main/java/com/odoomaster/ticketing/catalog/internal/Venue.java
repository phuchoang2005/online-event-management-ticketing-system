package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

/**
 * JPA entity mapping the persistence row for a venue.
 */
@Entity
@Table(name = "venues",
        uniqueConstraints = @UniqueConstraint(name = "uk_venue_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String address;

    /** Reference data: a factory, no lifecycle and nothing to transition. */
    public static Venue named(String name, String address) {
        Venue venue = new Venue();
        venue.name = Objects.requireNonNull(name, "name");
        venue.address = address;
        return venue;
    }
}
