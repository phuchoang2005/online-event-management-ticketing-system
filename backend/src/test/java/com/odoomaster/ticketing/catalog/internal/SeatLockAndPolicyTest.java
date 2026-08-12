package com.odoomaster.ticketing.catalog.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two value objects behind the seat hold: {@link SeatLock} (who holds it, until when) and
 * {@link LockPolicy} (how long a hold lasts).
 */
class SeatLockAndPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    // ── SeatLock ────────────────────────────────────────────────────────────────────────

    /**
     * The whole reason this type exists. As two loose nullable columns, "held by nobody until
     * midnight" and "held by user 7 until never" were both representable, and neither is a thing.
     */
    @Test
    void aHoldCannotExistWithoutBothAHolderAndAnExpiry() {
        assertThatThrownBy(() -> SeatLock.heldBy(null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holder");
        assertThatThrownBy(() -> SeatLock.heldBy(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiry");
    }

    @Test
    void hasExpiredAtIsFalseUpToAndIncludingTheExpiryInstant() {
        SeatLock lock = SeatLock.heldBy(7L, NOW);

        assertThat(lock.hasExpiredAt(NOW.minusSeconds(1))).isFalse();
        assertThat(lock.hasExpiredAt(NOW)).isFalse();
        assertThat(lock.hasExpiredAt(NOW.plusMillis(1))).isTrue();
    }

    @Test
    void twoHoldsWithTheSameHolderAndExpiryAreEqual() {
        assertThat(SeatLock.heldBy(7L, NOW))
                .isEqualTo(SeatLock.heldBy(7L, NOW))
                .hasSameHashCodeAs(SeatLock.heldBy(7L, NOW))
                .isNotEqualTo(SeatLock.heldBy(8L, NOW));
    }

    @Test
    void toStringNamesTheHolderAndExpiry_forReadableFailures() {
        assertThat(SeatLock.heldBy(7L, NOW).toString()).contains("7").contains(NOW.toString());
    }

    // ── LockPolicy ──────────────────────────────────────────────────────────────────────

    @Test
    void theProductionPolicyIsATenMinuteHold() {
        assertThat(LockPolicy.DEFAULT.ttl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(LockPolicy.DEFAULT.expiryFrom(NOW)).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void aNonPositiveTtlIsRejected() {
        // A zero or negative TTL would expire every hold the instant it was taken, silently
        // disabling the checkout window rather than failing loudly.
        assertThatThrownBy(() -> new LockPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockPolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockPolicy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCustomPolicyDrivesTheExpiry() {
        assertThat(new LockPolicy(Duration.ofSeconds(30)).expiryFrom(NOW)).isEqualTo(NOW.plusSeconds(30));
    }
}
