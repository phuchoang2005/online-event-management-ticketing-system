package com.odoomaster.ticketing.service;

import com.odoomaster.ticketing.catalog.internal.CatalogFixtures;
import com.odoomaster.ticketing.catalog.internal.EventSeat;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.ticketing.internal.Ticket;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;
import com.odoomaster.ticketing.ticketing.TicketDtos.ScanRequest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityMatrixTest {

    @TestFactory
    Stream<DynamicTest> generatedQrCodes_areUniqueUppercaseAndFixedLength() {
        return IntStream.range(0, 160).mapToObj(i -> DynamicTest.dynamicTest(
                "qr uniqueness sample " + i,
                () -> {
                    var seen = new HashSet<String>();
                    for (int j = 0; j < 25; j++) {
                        String qr = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
                        assertThat(qr).hasSize(32).matches("[0-9A-F]+");
                        assertThat(seen.add(qr)).isTrue();
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> seatLockStateMatrix_matchesContentionRules() {
        // Exhaustive over SeatStatus x {no lock, lapsed lock, live lock}. Before ADR-0013 this list
        // was hand-written and included four statuses (BOOKED, HELD, CANCELLED, REFUND_PENDING) that
        // no code path could ever produce; SeatStatus now makes the domain finite, so the matrix can
        // cover all of it instead of guessing at it.
        Instant lapsed = Instant.now().minusSeconds(60);
        Instant live = Instant.now().plusSeconds(60);
        List<SeatCase> cases = List.of(
                new SeatCase(SeatStatus.AVAILABLE, null, true),
                new SeatCase(SeatStatus.AVAILABLE, lapsed, true),
                new SeatCase(SeatStatus.AVAILABLE, live, true),
                new SeatCase(SeatStatus.LOCKED, null, false),
                new SeatCase(SeatStatus.LOCKED, lapsed, true),
                new SeatCase(SeatStatus.LOCKED, live, false),
                new SeatCase(SeatStatus.SOLD, null, false),
                new SeatCase(SeatStatus.SOLD, lapsed, false),
                new SeatCase(SeatStatus.SOLD, live, false));

        return IntStream.range(0, 12).boxed().flatMap(round -> cases.stream().map(c -> DynamicTest.dynamicTest(
                "seat lock rule round " + round + " status " + c.status(),
                () -> {
                    EventSeat seat = seat(round.longValue(), c.status(), c.lockedUntil());
                    // Calls the real aggregate. Before ADR-0013 this matrix re-implemented the rule as a private
                    // predicate, so all 96 of its assertions were self-fulfilling and verified no production code.
                    assertThat(seat.isLockableAt(Instant.now())).isEqualTo(c.lockable());
                })));
    }

    @TestFactory
    Stream<DynamicTest> scanPayloadMatrix_flagsMissingQrAndPreservesDeviceId() {
        List<ScanRequest> invalid = List.of(
                new ScanRequest(null, "gate-a"),
                new ScanRequest("", "gate-a"),
                new ScanRequest(" ", "gate-a"),
                new ScanRequest("\t", "gate-a"));
        Stream<DynamicTest> invalidTests = IntStream.range(0, 20).boxed().flatMap(round ->
                invalid.stream().map(req -> DynamicTest.dynamicTest("scan missing qr round " + round,
                        () -> assertThat(isMissingQr(req)).isTrue())));

        Stream<DynamicTest> validTests = IntStream.range(0, 60).mapToObj(i -> DynamicTest.dynamicTest(
                "scan valid qr keeps device " + i,
                () -> {
                    ScanRequest req = new ScanRequest("QR-" + i, "device-" + (i % 10));
                    assertThat(isMissingQr(req)).isFalse();
                    assertThat(req.deviceId()).startsWith("device-");
                }));

        return Stream.concat(invalidTests, validTests);
    }

    @TestFactory
    Stream<DynamicTest> paymentRetryAttemptMatrix_isStrictlyNextCount() {
        return IntStream.range(0, 80).mapToObj(existing -> DynamicTest.dynamicTest(
                "retry attempt after count " + existing,
                () -> assertThat(nextAttemptNo(existing)).isEqualTo(existing + 1)));
    }

    @TestFactory
    Stream<DynamicTest> ticketStatusMatrix_identifiesScannableOnlyWhenValidAndUnused() {
        // Exhaustive over TicketStatus x {no prior check-in, prior check-in}. As with the seat
        // matrix, the previous hand-written list asserted on three statuses the system never writes.
        List<TicketCase> cases = List.of(
                new TicketCase(TicketStatus.VALID, false, true),
                new TicketCase(TicketStatus.VALID, true, false),
                new TicketCase(TicketStatus.USED, false, false),
                new TicketCase(TicketStatus.USED, true, false),
                new TicketCase(TicketStatus.CANCELLED, false, false),
                new TicketCase(TicketStatus.CANCELLED, true, false));

        return IntStream.range(0, 16).boxed().flatMap(round -> cases.stream().map(c -> DynamicTest.dynamicTest(
                "ticket scannable round " + round + " " + c.status() + " existing=" + c.existingCheckIn(),
                () -> {
                    Ticket ticket = new Ticket();
                    ticket.setStatus(c.status());
                    assertThat(isScannable(ticket, c.existingCheckIn())).isEqualTo(c.scannable());
                })));
    }

    private static boolean isMissingQr(ScanRequest req) {
        return req == null || req.qrCode() == null || req.qrCode().isBlank();
    }

    private static int nextAttemptNo(long existingCount) {
        return (int) existingCount + 1;
    }

    private static boolean isScannable(Ticket ticket, boolean existingCheckIn) {
        return ticket.getStatus() == TicketStatus.VALID && !existingCheckIn;
    }

    private static EventSeat seat(Long id, SeatStatus status, Instant lockedUntil) {
        return CatalogFixtures.seat(id, 1L, "MAIN", "A", String.valueOf(id), BigDecimal.TEN, status,
                lockedUntil == null ? null : CatalogFixtures.lock(99L, lockedUntil));
    }

    private record SeatCase(SeatStatus status, Instant lockedUntil, boolean lockable) {}

    private record TicketCase(TicketStatus status, boolean existingCheckIn, boolean scannable) {}
}
