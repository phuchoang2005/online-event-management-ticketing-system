package com.odoomaster.ticketing.catalog.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

/**
 * JPA entity mapping the persistence row for a seat.
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seat_section_row_num",
                columnNames = {"section_id", "row_label", "seat_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "row_label", nullable = false, length = 8)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false, length = 8)
    private String seatNumber;

    /** Reference data: a factory, no lifecycle and nothing to transition. */
    public static Seat of(Long sectionId, String rowLabel, String seatNumber) {
        Seat seat = new Seat();
        seat.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        seat.rowLabel = Objects.requireNonNull(rowLabel, "rowLabel");
        seat.seatNumber = Objects.requireNonNull(seatNumber, "seatNumber");
        return seat;
    }
}
