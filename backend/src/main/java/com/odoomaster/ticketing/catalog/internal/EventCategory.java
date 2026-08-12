package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

/**
 * JPA entity mapping the persistence row for a eventcategory.
 */
@Entity
@Table(name = "event_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class EventCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** Reference data: a factory, no lifecycle and nothing to transition. */
    public static EventCategory named(String name) {
        EventCategory category = new EventCategory();
        category.name = Objects.requireNonNull(name, "name");
        return category;
    }
}
