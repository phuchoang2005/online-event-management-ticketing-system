package com.odoomaster.ticketing.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for the EventSeat aggregate.
 *
 * <p>Status is compared via a <strong>bound parameter</strong>, never a JPQL literal. Under
 * {@code @Enumerated(EnumType.STRING)} a hard-coded {@code s.status = 'SOLD'} is matched against the
 * mapped type by the Hibernate version in use rather than by the enum contract, so it can break
 * silently — and no test in this repo boots Hibernate to catch it (ADR-0013 rule 3). The
 * {@code default} wrappers below keep the call sites unchanged.
 */
public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {
  List<EventSeat> findByEventIdOrderByRowLabelAscSeatNumberAsc(Long eventId);

  List<EventSeat> findByIdIn(List<Long> ids);

  List<EventSeat> findByEventIdAndSection(Long eventId, String section);

  long countByEventId(Long eventId);

  long countByEventIdAndStatus(Long eventId, SeatStatus status);

  boolean existsByEventIdAndStatus(Long eventId, SeatStatus status);

  @Query("SELECT COALESCE(SUM(s.price), 0) FROM EventSeat s WHERE s.eventId = :eventId AND s.status = :status")
  java.math.BigDecimal sumPriceForEventByStatus(@Param("eventId") Long eventId, @Param("status") SeatStatus status);

  /** Revenue already realised for an event — the sum of its SOLD seats' prices. */
  default java.math.BigDecimal sumSoldPriceForEvent(Long eventId) {
    return sumPriceForEventByStatus(eventId, SeatStatus.SOLD);
  }

  @Query("SELECT COUNT(s) FROM EventSeat s WHERE s.status = :status")
  long countAllByStatus(@Param("status") SeatStatus status);

  /** Platform-wide count of sold seats. */
  default long countAllSold() {
    return countAllByStatus(SeatStatus.SOLD);
  }

  @Query("SELECT COUNT(s) FROM EventSeat s")
  long countAll();

  @Query("SELECT s FROM EventSeat s WHERE s.status = :status AND s.lock.lockedUntil < :now")
  List<EventSeat> findByStatusAndLockedUntilBefore(@Param("status") SeatStatus status, @Param("now") Instant now);

  /** Seats still marked LOCKED whose hold has lapsed — the sweeper's work queue. */
  default List<EventSeat> findExpiredLocks(Instant now) {
    return findByStatusAndLockedUntilBefore(SeatStatus.LOCKED, now);
  }
}
