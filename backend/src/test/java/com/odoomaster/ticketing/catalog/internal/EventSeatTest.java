package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The seat state machine, tested directly against the aggregate — no mocks, no repository, no clock
 * bean, no Spring.
 *
 * <p>This is what moving the rules out of {@code SeatInventoryImpl} buys. Every case below used to
 * require stubbing a repository just to observe a status transition, and the lock-expiry cases were
 * only reachable through a service method. Here they are ordinary method calls on an object.
 */
class EventSeatTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);
    private static final BigDecimal PRICE = new BigDecimal("150000");

    private static EventSeat available() {
        return EventSeat.create(1L, 7L, 3L, "MAIN", "A", "12", PRICE);
    }

    @Nested
    class Creation {

        @Test
        void createStartsAvailableUnheldAndVersionZero() {
            EventSeat seat = available();

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(seat.lockedBy()).isNull();
            assertThat(seat.lockedUntil()).isNull();
            assertThat(seat.getVersion()).isZero();
            assertThat(seat.label()).isEqualTo("A-12");
        }

        @Test
        void createRejectsANegativePrice() {
            assertThatThrownBy(() -> EventSeat.create(1L, 7L, 3L, "MAIN", "A", "12", new BigDecimal("-1")))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("cannot be negative");
        }
    }

    @Nested
    class Locking {

        @Test
        void lockForHoldsTheSeatForTheBuyerUntilTheTtlElapses() {
            EventSeat seat = available();

            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.LOCKED);
            assertThat(seat.lockedBy()).isEqualTo(42L);
            assertThat(seat.lockedUntil()).isEqualTo(NOW.plusSeconds(600));
        }

        @Test
        void aLiveHoldRejectsASecondBuyer_preventingDoubleBooking() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            assertThatThrownBy(() -> seat.lockFor(99L, NOW.plusSeconds(60), LockPolicy.DEFAULT))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("A-12 is no longer available");
            assertThat(seat.lockedBy()).as("the first buyer keeps the hold").isEqualTo(42L);
        }

        @Test
        void aLapsedHoldIsReclaimedByTheNextBuyer() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            // Eleven minutes later the ten-minute hold has lapsed, even though the sweeper has not run.
            seat.lockFor(99L, NOW.plusSeconds(660), LockPolicy.DEFAULT);

            assertThat(seat.lockedBy()).isEqualTo(99L);
        }

        @Test
        void aSoldSeatIsNeverLockable() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);
            seat.markSold(NOW);

            assertThat(seat.isLockableAt(LATER)).isFalse();
            assertThatThrownBy(() -> seat.lockFor(99L, LATER, LockPolicy.DEFAULT))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        void theHoldExpiresExactlyAtTheTtlBoundary() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);
            Instant expiry = NOW.plusSeconds(600);

            // At the boundary the hold is still good — expiry is strictly "before now".
            assertThat(seat.isLockExpiredAt(expiry)).isFalse();
            assertThat(seat.isLockExpiredAt(expiry.plusMillis(1))).isTrue();
        }
    }

    @Nested
    class Selling {

        @Test
        void markSoldCompletesTheSaleAndClearsTheHold() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            seat.markSold(NOW.plusSeconds(60));

            assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
            assertThat(seat.isSold()).isTrue();
            assertThat(seat.lockedBy()).isNull();
            assertThat(seat.lockedUntil()).isNull();
        }

        /**
         * The guard order is load-bearing: a buyer whose seat was sold out from under them and a
         * buyer who simply took too long deserve different messages, and both conditions are true of
         * an already-sold seat whose old hold has lapsed.
         */
        @Test
        void anAlreadySoldSeatReportsSeatTakenRatherThanLockExpired() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);
            seat.markSold(NOW);

            assertThatThrownBy(() -> seat.markSold(LATER))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already sold");
        }

        @Test
        void aLapsedHoldCannotBeConvertedToASale() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            assertThatThrownBy(() -> seat.markSold(NOW.plusSeconds(660)))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("lock expired");
        }
    }

    @Nested
    class Releasing {

        @Test
        void releaseHoldFreesAHeldSeatOnly() {
            EventSeat held = available();
            held.lockFor(42L, NOW, LockPolicy.DEFAULT);
            held.releaseHold();
            assertThat(held.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            assertThat(held.lockedBy()).isNull();

            EventSeat sold = available();
            sold.lockFor(42L, NOW, LockPolicy.DEFAULT);
            sold.markSold(NOW);
            sold.releaseHold();
            assertThat(sold.getStatus()).as("cancelling an order must not un-sell a paid seat")
                    .isEqualTo(SeatStatus.SOLD);
        }

        @Test
        void releaseSaleFreesASoldSeatOnly() {
            EventSeat sold = available();
            sold.lockFor(42L, NOW, LockPolicy.DEFAULT);
            sold.markSold(NOW);
            sold.releaseSale();
            assertThat(sold.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

            EventSeat held = available();
            held.lockFor(42L, NOW, LockPolicy.DEFAULT);
            held.releaseSale();
            assertThat(held.getStatus()).as("a ticket cancellation must not steal someone's hold")
                    .isEqualTo(SeatStatus.LOCKED);
        }

        @Test
        void releaseExpiredLockReclaimsOnlyLapsedHoldsAndReportsWhetherItActed() {
            EventSeat lapsed = available();
            lapsed.lockFor(42L, NOW, LockPolicy.DEFAULT);
            assertThat(lapsed.releaseExpiredLock(NOW.plusSeconds(660))).isTrue();
            assertThat(lapsed.getStatus()).isEqualTo(SeatStatus.AVAILABLE);

            EventSeat live = available();
            live.lockFor(42L, NOW, LockPolicy.DEFAULT);
            assertThat(live.releaseExpiredLock(NOW.plusSeconds(60)))
                    .as("a hold renewed since the sweeper's query must not be yanked from its buyer")
                    .isFalse();
            assertThat(live.lockedBy()).isEqualTo(42L);

            assertThat(available().releaseExpiredLock(LATER)).isFalse();
        }
    }

    @Nested
    class AdminEdits {

        @Test
        void repriceIsRefusedOnceTheSeatIsSold() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);
            seat.markSold(NOW);

            // sumSoldPriceForEvent totals sold seats' prices, so this would rewrite reported revenue.
            assertThatThrownBy(() -> seat.reprice(new BigDecimal("1")))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already been sold");
            assertThat(seat.getPrice()).isEqualByComparingTo(PRICE);
        }

        @ParameterizedTest
        @EnumSource(value = SeatStatus.class, names = {"AVAILABLE", "LOCKED"})
        void repriceIsAllowedWhileTheSeatIsUnsold(SeatStatus status) {
            EventSeat seat = available();
            if (status == SeatStatus.LOCKED) seat.lockFor(42L, NOW, LockPolicy.DEFAULT);

            seat.reprice(new BigDecimal("200000"));

            assertThat(seat.getPrice()).isEqualByComparingTo("200000");
        }

        @Test
        void relabelSectionIsAllowedEvenForSoldSeats() {
            EventSeat seat = available();
            seat.lockFor(42L, NOW, LockPolicy.DEFAULT);
            seat.markSold(NOW);

            // A section name is a display label, not part of the sale.
            assertThatNoException().isThrownBy(() -> seat.relabelSection("PREMIUM"));
            assertThat(seat.getSection()).isEqualTo("PREMIUM");
        }
    }

    @Nested
    class EventOwnership {

        @Test
        void requireBelongsToRejectsASeatFromAnotherEvent() {
            EventSeat seat = available();

            assertThat(seat.belongsTo(1L)).isTrue();
            assertThatNoException().isThrownBy(() -> seat.requireBelongsTo(1L));
            assertThatThrownBy(() -> seat.requireBelongsTo(2L))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not in this event");
        }
    }
}
