package com.odoomaster.ticketing.sales;

import org.springframework.modulith.NamedInterface;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Published sales API exposing order/payment aggregates for reporting.
 *
 * <p>{@code analytics} calls this instead of reaching into sales' {@code Order}/{@code Payment}
 * entities or their repositories, so the sales schema stays private to the module. All figures are
 * derived from paid orders and recorded payments.
 *
 * <p>The whole of {@code sales}' cross-module surface, exposed as {@code sales::reporting};
 * {@code OrderService} and {@code PaymentRetryService} are deliberately not published — ordering is
 * driven through sales' own controller only.
 */
@NamedInterface("reporting")
public interface SalesReporting {

    /** Total revenue over all {@code PAID} orders. */
    BigDecimal totalPaidRevenue();

    /** Revenue over {@code PAID} orders for a single event. */
    BigDecimal paidRevenueForEvent(Long eventId);

    /**
     * Daily revenue for {@code PAID} orders whose {@code paidAt} is on/after {@code from}, one row per
     * day with a recorded paid order (days with none are simply absent).
     *
     * @param from inclusive lower bound on {@code paidAt}
     * @return the per-day revenue points in ascending date order
     */
    List<DailyRevenue> revenueByDay(Instant from);

    /** Count of orders in the given status. */
    long countOrdersByStatus(String status);

    /** Count of payments in the given status. */
    long countPaymentsByStatus(String status);

    /** One day's paid-order revenue and order count. */
    @NamedInterface("reporting")
    record DailyRevenue(LocalDate date, BigDecimal revenue, long orderCount) {}
}
