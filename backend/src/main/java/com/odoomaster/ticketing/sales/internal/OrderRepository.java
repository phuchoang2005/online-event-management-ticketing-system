package com.odoomaster.ticketing.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository for the Order aggregate.
 *
 * <p>Status is compared via a <strong>bound parameter</strong>, never a JPQL literal — see
 * {@link com.odoomaster.ticketing.catalog.internal.EventSeatRepository} for why. The {@code default}
 * wrappers preserve the existing call sites in {@code SalesReportingImpl}.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

  @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
  BigDecimal sumRevenueByStatus(@Param("status") OrderStatus status);

  /** Lifetime revenue: the sum of every PAID order. */
  default BigDecimal sumPaidRevenue() {
    return sumRevenueByStatus(OrderStatus.PAID);
  }

  @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status AND o.paidAt >= :from")
  BigDecimal sumRevenueByStatusSince(@Param("status") OrderStatus status, @Param("from") Instant from);

  /** Revenue from PAID orders settled at or after {@code from}. */
  default BigDecimal sumPaidRevenueSince(Instant from) {
    return sumRevenueByStatusSince(OrderStatus.PAID, from);
  }

  @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status AND o.eventId = :eventId")
  BigDecimal sumRevenueByStatusForEvent(@Param("status") OrderStatus status, @Param("eventId") Long eventId);

  /** Revenue from PAID orders for one event. */
  default BigDecimal sumPaidRevenueForEvent(Long eventId) {
    return sumRevenueByStatusForEvent(OrderStatus.PAID, eventId);
  }

  @Query("SELECT FUNCTION('DATE', o.paidAt) AS d, " +
      "COALESCE(SUM(o.totalAmount), 0) AS revenue, " +
      "COUNT(o) AS cnt " +
      "FROM Order o " +
      "WHERE o.status = :status AND o.paidAt >= :from " +
      "GROUP BY FUNCTION('DATE', o.paidAt) " +
      "ORDER BY d ASC")
  List<Object[]> revenueByDayForStatus(@Param("status") OrderStatus status, @Param("from") Instant from);

  /** Daily revenue series over PAID orders, oldest first. */
  default List<Object[]> revenueByDay(Instant from) {
    return revenueByDayForStatus(OrderStatus.PAID, from);
  }

  long countByStatus(OrderStatus status);

  long countByEventIdAndStatusNotIn(Long eventId, Collection<OrderStatus> excludedStatuses);

  List<Order> findByEventId(Long eventId);
}
