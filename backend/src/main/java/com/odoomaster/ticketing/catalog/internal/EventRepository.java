package com.odoomaster.ticketing.catalog.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for the Event aggregate.
 */
public interface EventRepository extends JpaRepository<Event, Long> {
  List<Event> findAllByStatusOrderByStartTimeAsc(EventStatus status);

  long countByStatus(EventStatus status);

  @Query("SELECT e FROM Event e ORDER BY e.createdAt DESC")
  List<Event> findAllForAdmin();

  @Query("SELECT DISTINCT e FROM Event e LEFT JOIN e.categories c " +
      "WHERE e.status = :status " +
      "AND (:category IS NULL OR c.name = :category) " +
      "AND (:q IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(e.location) LIKE LOWER(CONCAT('%', :q, '%'))) "
      +
      "ORDER BY e.startTime ASC")
  org.springframework.data.domain.Page<Event> findPublished(EventStatus status, String category, String q,
      org.springframework.data.domain.Pageable pageable);

  @Query("SELECT e FROM Event e LEFT JOIN com.odoomaster.ticketing.catalog.internal.EventSeat s ON s.eventId = e.id " +
      "WHERE e.status = :status AND e.startTime >= :now " +
      "GROUP BY e " +
      "ORDER BY COUNT(s.id) DESC, e.startTime ASC")
  List<Event> findTrending(EventStatus status, Instant now, Pageable pageable);
}
