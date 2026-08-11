package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.sales.SalesReporting;
import com.odoomaster.ticketing.sales.SalesReporting.DailyRevenue;
import com.odoomaster.ticketing.sales.internal.OrderRepository;
import com.odoomaster.ticketing.sales.internal.OrderStatus;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import com.odoomaster.ticketing.sales.internal.PaymentRepository;
import com.odoomaster.ticketing.sales.internal.SalesReportingImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Reliability tests for {@link SalesReportingImpl} — the published sales reporting API. Focuses on the
 * {@code revenueByDay} tuple→{@link DailyRevenue} mapping (moved out of {@code AnalyticsService} in
 * Sprint 3), including the varied date types the JPQL {@code FUNCTION('DATE', ...)} can return.
 */
@ExtendWith(MockitoExtension.class)
class SalesReportingReliabilityTest {

    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;

    SalesReporting reporting;

    @BeforeEach
    void setUp() {
        reporting = new SalesReportingImpl(orders, payments);
    }

    @Test
    void revenueByDay_mapsSqlDateLocalDateAndNullRevenue() {
        Object[] sqlDateRow = {java.sql.Date.valueOf("2026-08-01"), new BigDecimal("150000"), 3L};
        Object[] localDateRow = {LocalDate.parse("2026-08-02"), new BigDecimal("0"), 0L};
        Object[] nullRevenueRow = {java.sql.Date.valueOf("2026-08-03"), null, 2L};
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        when(orders.revenueByDay(from)).thenReturn(List.of(sqlDateRow, localDateRow, nullRevenueRow));

        List<DailyRevenue> out = reporting.revenueByDay(from);

        assertThat(out).extracting(DailyRevenue::date)
                .containsExactly(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-02"),
                        LocalDate.parse("2026-08-03"));
        assertThat(out.get(0).revenue()).isEqualByComparingTo("150000");
        assertThat(out.get(0).orderCount()).isEqualTo(3L);
        assertThat(out.get(2).revenue()).isEqualByComparingTo("0"); // null revenue -> ZERO
        assertThat(out.get(2).orderCount()).isEqualTo(2L);
    }

    @Test
    void aggregates_delegateToRepositories() {
        when(orders.sumPaidRevenue()).thenReturn(new BigDecimal("800000"));
        when(orders.sumPaidRevenueForEvent(5L)).thenReturn(new BigDecimal("120000"));
        when(orders.countByStatus(OrderStatus.CANCELLED)).thenReturn(4L);
        when(payments.countByStatus(PaymentStatus.SUCCEEDED)).thenReturn(11L);

        assertThat(reporting.totalPaidRevenue()).isEqualByComparingTo("800000");
        assertThat(reporting.paidRevenueForEvent(5L)).isEqualByComparingTo("120000");
        assertThat(reporting.countOrdersByStatus("CANCELLED")).isEqualTo(4L);
        assertThat(reporting.countPaymentsByStatus("SUCCEEDED")).isEqualTo(11L);
    }
}
