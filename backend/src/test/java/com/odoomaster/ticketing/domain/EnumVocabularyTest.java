package com.odoomaster.ticketing.domain;

import com.odoomaster.ticketing.catalog.internal.EventStatus;
import com.odoomaster.ticketing.catalog.internal.SeatStatus;
import com.odoomaster.ticketing.feedback.internal.FeedbackCategory;
import com.odoomaster.ticketing.feedback.internal.FeedbackStatus;
import com.odoomaster.ticketing.iam.internal.UserStatus;
import com.odoomaster.ticketing.notification.internal.NotificationChannel;
import com.odoomaster.ticketing.notification.internal.NotificationStatus;
import com.odoomaster.ticketing.sales.internal.OrderStatus;
import com.odoomaster.ticketing.sales.internal.PaymentRetryStatus;
import com.odoomaster.ticketing.sales.payment.PaymentMethod;
import com.odoomaster.ticketing.sales.payment.PaymentStatus;
import com.odoomaster.ticketing.ticketing.internal.CheckInStatus;
import com.odoomaster.ticketing.ticketing.internal.TicketStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the 13 status/vocabulary enums introduced by ADR-0013 against the strings that are actually
 * on disk and on the wire.
 *
 * <p><strong>Why this test carries so much weight.</strong> The enums are mapped with
 * {@code @Enumerated(EnumType.STRING)} onto pre-existing VARCHAR columns, and the refactor is
 * required to be schema-neutral and wire-neutral: every persisted value must stay byte-identical,
 * and the Next.js frontend pins the same strings as TypeScript unions. No test in this repository
 * boots Hibernate or a database, so a renamed constant would otherwise sail through {@code mvn test}
 * and only surface as unreadable rows in production. Asserting {@code name()} here is the one guard
 * the plain unit suite can actually provide.
 *
 * <p>It also pins the lenient {@code parse} contract every enum publishes: unknown input yields
 * {@link Optional#empty()} rather than throwing, which is what keeps {@code analytics} — which asks
 * about statuses the domain deliberately does not model — from 500-ing the admin dashboard.
 */
class EnumVocabularyTest {

    /**
     * The exact on-disk vocabulary per enum. <strong>Changing a string here is a schema change</strong>
     * and requires a Flyway migration to rewrite existing rows plus a frontend type update.
     */
    private static final Map<Class<? extends Enum<?>>, List<String>> VOCABULARIES = Map.ofEntries(
            Map.entry(SeatStatus.class, List.of("AVAILABLE", "LOCKED", "SOLD")),
            Map.entry(EventStatus.class, List.of("DRAFT", "PUBLISHED", "CANCELLED", "COMPLETED")),
            Map.entry(OrderStatus.class, List.of("PENDING", "PAID", "CANCELLED", "REFUNDED")),
            Map.entry(PaymentStatus.class, List.of("SUCCEEDED", "FAILED", "PENDING")),
            Map.entry(PaymentMethod.class, List.of("MOMO", "VNPAY", "MOCK")),
            Map.entry(PaymentRetryStatus.class, List.of("SUCCEEDED", "FAILED")),
            Map.entry(TicketStatus.class, List.of("VALID", "USED", "CANCELLED")),
            Map.entry(CheckInStatus.class, List.of("OK")),
            Map.entry(FeedbackStatus.class, List.of("NEW", "READ", "RESOLVED")),
            Map.entry(FeedbackCategory.class, List.of("GENERAL", "EVENT", "PAYMENT", "BUG_REPORT", "SUGGESTION")),
            Map.entry(NotificationStatus.class, List.of("SENT")),
            Map.entry(NotificationChannel.class, List.of("IN_APP", "EMAIL", "SMS")),
            Map.entry(UserStatus.class, List.of("ACTIVE")));

    /** Column widths from the Flyway baseline, so a new constant cannot silently outgrow its column. */
    private static final Map<Class<? extends Enum<?>>, Integer> COLUMN_LENGTHS = Map.ofEntries(
            Map.entry(SeatStatus.class, 16),
            Map.entry(EventStatus.class, 20),
            Map.entry(OrderStatus.class, 20),
            Map.entry(PaymentStatus.class, 20),
            Map.entry(PaymentMethod.class, 20),
            Map.entry(PaymentRetryStatus.class, 20),
            Map.entry(TicketStatus.class, 20),
            Map.entry(CheckInStatus.class, 20),
            Map.entry(FeedbackStatus.class, 20),
            Map.entry(FeedbackCategory.class, 32),
            Map.entry(NotificationStatus.class, 20),
            Map.entry(NotificationChannel.class, 20),
            Map.entry(UserStatus.class, 20));

    @TestFactory
    Stream<DynamicTest> everyEnumPersistsExactlyTheLegacyStrings() {
        return VOCABULARIES.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().getSimpleName() + " persists its legacy vocabulary",
                () -> {
                    List<String> actual = Stream.of(entry.getKey().getEnumConstants())
                            .map(Enum::name)
                            .toList();
                    assertThat(actual)
                            .as("on-disk vocabulary of %s", entry.getKey().getSimpleName())
                            .containsExactlyInAnyOrderElementsOf(entry.getValue());
                }));
    }

    @TestFactory
    Stream<DynamicTest> everyConstantFitsItsColumn() {
        return COLUMN_LENGTHS.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().getSimpleName() + " fits VARCHAR(" + entry.getValue() + ")",
                () -> {
                    for (Object constant : entry.getKey().getEnumConstants()) {
                        String name = ((Enum<?>) constant).name();
                        assertThat(name.length())
                                .as("%s.%s must fit the migrated column", entry.getKey().getSimpleName(), name)
                                .isLessThanOrEqualTo(entry.getValue());
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> parseRoundTripsEveryConstant() {
        return VOCABULARIES.keySet().stream().map(type -> DynamicTest.dynamicTest(
                type.getSimpleName() + ".parse round-trips every constant",
                () -> {
                    for (Object constant : type.getEnumConstants()) {
                        String name = ((Enum<?>) constant).name();
                        assertThat(parse(type, name)).contains(constant);
                        // Callers hand us raw request/query strings, so casing and padding are tolerated.
                        assertThat(parse(type, name.toLowerCase())).contains(constant);
                        assertThat(parse(type, "  " + name + "  ")).contains(constant);
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> parseIsLenientRatherThanThrowing() {
        return VOCABULARIES.keySet().stream().map(type -> DynamicTest.dynamicTest(
                type.getSimpleName() + ".parse returns empty for unknown input",
                () -> {
                    assertThat(parse(type, null)).isEmpty();
                    assertThat(parse(type, "")).isEmpty();
                    assertThat(parse(type, "   ")).isEmpty();
                    assertThat(parse(type, "NOT_A_REAL_STATUS")).isEmpty();
                }));
    }

    /**
     * The statuses {@code AnalyticsService} counts but no code path ever writes. They must parse to
     * empty — the reporting facets turn that into {@code 0}, preserving the behaviour the raw-string
     * columns had. Modelling them would be worse: the dashboard would start reporting invented
     * states as real ones.
     */
    @Test
    void analyticsFictionsAreDeliberatelyNotModelled() {
        assertThat(Set.of("EXPIRED", "REFUND_PENDING"))
                .allSatisfy(fiction -> assertThat(OrderStatus.parse(fiction))
                        .as("OrderStatus should not model the analytics-only value %s", fiction)
                        .isEmpty());
    }

    /**
     * Invoke the enum's static {@code parse(String)} reflectively. Every vocabulary enum declares
     * one, but they share no interface (a static method cannot be inherited), so reflection is what
     * lets one test cover all 13 uniformly — and it fails loudly if an enum ever drops the method.
     */
    @SuppressWarnings("unchecked")
    private static Optional<Object> parse(Class<? extends Enum<?>> type, String raw) throws Exception {
        Method parse = type.getDeclaredMethod("parse", String.class);
        return (Optional<Object>) parse.invoke(null, raw);
    }
}
