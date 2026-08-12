package com.odoomaster.ticketing.sales.internal;

import com.odoomaster.ticketing.sales.payment.PaymentMethod;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import com.odoomaster.ticketing.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order/payment aggregates and {@link Money}, tested with no mocks and no Spring.
 */
class OrderAggregateTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Nested
    class MoneyValue {

        @Test
        void sumTotalsSeatPricesAndAnEmptyOrderCostsNothing() {
            assertThat(Money.sum(List.of(new BigDecimal("100000"), new BigDecimal("150000"))))
                    .isEqualTo(Money.of(new BigDecimal("250000")));
            assertThat(Money.sum(List.of())).isEqualTo(Money.zero());
            assertThat(Money.zero().isZero()).isTrue();
        }

        @Test
        void negativeAmountsAreRejectedAtConstruction() {
            assertThatThrownBy(() -> Money.of(new BigDecimal("-1")))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("cannot be negative");
        }

        /** {@code BigDecimal.equals} compares scale; money does not care that 100 is written 100.00. */
        @Test
        void equalityIgnoresScale() {
            assertThat(Money.of(new BigDecimal("100")))
                    .isEqualTo(Money.of(new BigDecimal("100.00")))
                    .hasSameHashCodeAs(Money.of(new BigDecimal("100.00")));
        }

        @Test
        void plusAdds() {
            assertThat(Money.of(BigDecimal.TEN).plus(Money.of(BigDecimal.ONE)))
                    .isEqualTo(Money.of(new BigDecimal("11")));
        }
    }

    @Nested
    class OrderLifecycle {

        private Order pending() {
            return Order.place(5L, 1L, Money.of(new BigDecimal("250000")), NOW);
        }

        @Test
        void placeStartsPendingOwnedByTheBuyer() {
            Order order = pending();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.isPayable()).isTrue();
            assertThat(order.isPaid()).isFalse();
            assertThat(order.isOwnedBy(5L)).isTrue();
            assertThat(order.isOwnedBy(6L)).isFalse();
            assertThat(order.total()).isEqualTo(Money.of(new BigDecimal("250000")));
            assertThat(order.getPaidAt()).isNull();
        }

        @Test
        void paySettlesTheOrderAndStampsMethodAndTime() {
            Order order = pending();

            order.pay(PaymentMethod.MOMO, NOW.plusSeconds(120));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.isPaid()).isTrue();
            assertThat(order.isPayable()).isFalse();
            assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.MOMO);
            assertThat(order.getPaidAt()).isEqualTo(NOW.plusSeconds(120));
        }

        /** A retried request must not overwrite the original settlement. */
        @Test
        void payIsIdempotentAndKeepsTheOriginalMethodAndTimestamp() {
            Order order = pending();
            order.pay(PaymentMethod.MOMO, NOW);

            assertThatNoException().isThrownBy(() -> order.pay(PaymentMethod.VNPAY, NOW.plusSeconds(600)));

            assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.MOMO);
            assertThat(order.getPaidAt()).isEqualTo(NOW);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "REFUNDED"})
        void aTerminalOrderCannotBePaid(OrderStatus status) {
            Order order = SalesFixtures.order(1L, status, 5L);

            assertThatThrownBy(() -> order.pay(PaymentMethod.MOCK, NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("cannot be paid in state " + status);
        }

        @Test
        void cancelAbandonsAPendingOrderAndIsIdempotent() {
            Order order = pending();

            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            assertThatNoException().isThrownBy(order::cancel);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void aPaidOrderCannotBeCancelled_becauseRefundingIsADifferentOperation() {
            Order order = pending();
            order.pay(PaymentMethod.MOCK, NOW);

            assertThatThrownBy(order::cancel)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Cannot cancel a paid order");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @Nested
    class PaymentRecords {

        @Test
        void recordCapturesTheGatewayOutcome() {
            Payment p = Payment.record(7L, PaymentMethod.VNPAY, "TXN-1",
                    Money.of(new BigDecimal("250000")), PaymentStatus.SUCCEEDED, NOW);

            assertThat(p.getOrderId()).isEqualTo(7L);
            assertThat(p.getProvider()).isEqualTo(PaymentMethod.VNPAY);
            assertThat(p.isSucceeded()).isTrue();
            assertThat(p.amount()).isEqualTo(Money.of(new BigDecimal("250000")));
            assertThat(p.getCreatedAt()).isEqualTo(NOW);
        }

        /** A decline is still a payment row — that is what makes the funnel able to report non-zero. */
        @Test
        void aDeclinedChargeIsRecordedWithNoTransactionId() {
            Payment p = Payment.record(7L, PaymentMethod.MOMO, null,
                    Money.of(BigDecimal.TEN), PaymentStatus.FAILED, NOW);

            assertThat(p.isSucceeded()).isFalse();
            assertThat(p.getTransactionId()).isNull();
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        void orderItemRecordsTheAgreedPrice() {
            OrderItem item = OrderItem.forSeat(7L, 10L, 3L, Money.of(new BigDecimal("100000")));

            assertThat(item.getOrderId()).isEqualTo(7L);
            assertThat(item.getEventSeatId()).isEqualTo(10L);
            assertThat(item.amount()).isEqualTo(Money.of(new BigDecimal("100000")));
        }
    }

    @Nested
    class Retries {

        @Test
        void attemptNumbersFollowTheExistingCount() {
            assertThat(PaymentRetry.nextAttemptNo(0)).isEqualTo(1);
            assertThat(PaymentRetry.nextAttemptNo(4)).isEqualTo(5);
        }

        @Test
        void anAttemptRecordsTheDeclineReason() {
            PaymentRetry retry = PaymentRetry.attempt(9L, PaymentRetryStatus.FAILED, 2, "GATEWAY_500", NOW);

            assertThat(retry.getPaymentId()).isEqualTo(9L);
            assertThat(retry.getAttemptNo()).isEqualTo(2);
            assertThat(retry.getErrorCode()).isEqualTo("GATEWAY_500");
            assertThat(retry.getAttemptedAt()).isEqualTo(NOW);
        }

        @Test
        void attemptNumbersStartAtOne() {
            assertThatThrownBy(() -> PaymentRetry.attempt(9L, PaymentRetryStatus.FAILED, 0, null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
