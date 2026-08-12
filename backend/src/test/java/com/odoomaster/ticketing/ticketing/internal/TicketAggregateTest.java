package com.odoomaster.ticketing.ticketing.internal;

import com.odoomaster.ticketing.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The ticket lifecycle and {@link QrCode}, with no mocks and no Spring. */
class TicketAggregateTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    private static Ticket issued() {
        return Ticket.issue(5L, 4L, 2L, 3L, QrCode.generate(), NOW);
    }

    @Nested
    class Issuance {

        @Test
        void issueStartsValidAndScannable() {
            Ticket t = issued();

            assertThat(t.getStatus()).isEqualTo(TicketStatus.VALID);
            assertThat(t.isScannable(false)).isTrue();
            assertThat(t.isOwnedBy(4L)).isTrue();
            assertThat(t.isOwnedBy(9L)).isFalse();
            assertThat(t.getIssuedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class GateScanning {

        @Test
        void markUsedAdmitsTheHolderOnce() {
            Ticket t = issued();

            t.markUsed();

            assertThat(t.getStatus()).isEqualTo(TicketStatus.USED);
            assertThat(t.isScannable(false)).isFalse();
        }

        /**
         * Guard order matters at the gate: a steward facing "already used" looks for a duplicate
         * ticket, while "not valid" means the holder refunded it. Swapping them sends them hunting
         * for the wrong problem.
         */
        @Test
        void aSecondScanReportsAlreadyUsedRatherThanNotValid() {
            Ticket t = issued();
            t.markUsed();

            assertThatThrownBy(t::markUsed)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already checked in");
        }

        @Test
        void aCancelledTicketIsRefusedAsNotValid() {
            Ticket t = issued();
            t.cancel();

            assertThatThrownBy(t::markUsed)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("not in VALID state");
        }

        /** An existing check-in row makes a ticket unscannable even while its status says VALID. */
        @ParameterizedTest
        @EnumSource(TicketStatus.class)
        void aPriorCheckInMakesAnyTicketUnscannable(TicketStatus status) {
            assertThat(TicketingFixtures.ticket(1L, status).isScannable(true)).isFalse();
        }
    }

    @Nested
    class Cancellation {

        @Test
        void cancelWithdrawsAValidTicketAndIsIdempotent() {
            Ticket t = issued();

            t.cancel();
            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);

            assertThatNoException().isThrownBy(t::cancel);
            assertThat(t.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        }

        @Test
        void aUsedTicketCannotBeCancelled_becauseAttendanceIsHistory() {
            Ticket t = issued();
            t.markUsed();

            assertThatThrownBy(t::cancel)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already been checked in");
            assertThat(t.getStatus()).isEqualTo(TicketStatus.USED);
        }
    }

    @Nested
    class QrCodes {

        /**
         * These assertions used to live inline in {@code ReliabilityMatrixTest}, re-implementing the
         * generator rather than calling it — so they verified a copy of the production code.
         */
        @RepeatedTest(20)
        void generateProducesA32CharacterUppercaseHexCode() {
            QrCode qr = QrCode.generate();

            assertThat(qr.value()).hasSize(32).matches("[0-9A-F]+");
            assertThat(qr.toString()).isEqualTo(qr.value());
        }

        @Test
        void generatedCodesAreUnique() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                assertThat(seen.add(QrCode.generate().value()))
                        .as("QR codes must never collide — they are ticket identity")
                        .isTrue();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "0123456789ABCDEF0123456789ABCDE", "0123456789abcdef0123456789abcdef",
                "0123456789ABCDEF0123456789ABCDEFF", "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"})
        void malformedCodesAreRejected(String raw) {
            assertThatThrownBy(() -> new QrCode(raw)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullIsRejected() {
            assertThatThrownBy(() -> new QrCode(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CheckInRecords {

        @Test
        void recordCapturesWhoScannedWhatAndWhen() {
            CheckIn ci = CheckIn.record(1L, 9L, "gate-3", NOW);

            assertThat(ci.getTicketId()).isEqualTo(1L);
            assertThat(ci.getCheckedInBy()).isEqualTo(9L);
            assertThat(ci.getDeviceId()).isEqualTo("gate-3");
            assertThat(ci.getStatus()).isEqualTo(CheckInStatus.OK);
            assertThat(ci.getCheckedInAt()).isEqualTo(NOW);
        }
    }
}
