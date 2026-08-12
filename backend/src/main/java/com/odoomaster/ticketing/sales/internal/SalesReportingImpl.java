package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.sales.SalesReporting;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * sales-owned implementation of {@link SalesReporting}. Wraps the order/payment
 * repositories and maps
 * the raw {@code revenueByDay} tuples into the published {@link DailyRevenue}
 * projection so callers
 * never touch the entities.
 *
 * <p><strong>Status arguments are lenient by contract.</strong> They arrive as {@code String} from
 * {@code analytics}, which asks about statuses this module does not model. An unrecognised value
 * answers {@code 0} — the same answer the raw-string column gave — rather than throwing and taking
 * the whole admin dashboard down with it (ADR-0013 §1).
 */
@Service
@Transactional(readOnly = true)
public class SalesReportingImpl implements SalesReporting {

  private final OrderRepository orders;
  private final PaymentRepository payments;

  public SalesReportingImpl(OrderRepository orders, PaymentRepository payments) {
    this.orders = orders;
    this.payments = payments;
  }

  @Override
  public BigDecimal totalPaidRevenue() {
    return orders.sumPaidRevenue();
  }

  @Override
  public BigDecimal paidRevenueForEvent(Long eventId) {
    return orders.sumPaidRevenueForEvent(eventId);
  }

  @Override
  public List<DailyRevenue> revenueByDay(Instant from) {
    return orders.revenueByDay(from).stream()
        .map(r -> new DailyRevenue(
            toLocalDate(r[0]),
            r[1] == null ? BigDecimal.ZERO : new BigDecimal(r[1].toString()),
            ((Number) r[2]).longValue()))
        .toList();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Anti-corruption boundary — see the class javadoc. {@code analytics} counts
   * {@code "EXPIRED"} and {@code "REFUND_PENDING"}, neither of which {@link OrderStatus} models,
   * so those answer {@code 0} exactly as they did when the column was a raw string.
   */
  @Override
  public long countOrdersByStatus(String status) {
    return OrderStatus.parse(status).map(orders::countByStatus).orElse(0L);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Anti-corruption boundary — unknown status answers {@code 0}, never throws.
   */
  @Override
  public long countPaymentsByStatus(String status) {
    return PaymentStatus.parse(status).map(payments::countByStatus).orElse(0L);
  }

  private static LocalDate toLocalDate(Object value) {
    if (value instanceof LocalDate ld)
      return ld;
    if (value instanceof Date d)
      return d.toLocalDate();
    if (value instanceof java.util.Date d)
      return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    return LocalDate.parse(value.toString());
  }
}
