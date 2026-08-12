package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.shared.DomainException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The event publication lifecycle and {@link TicketType} allocation, with no mocks. */
class EventAggregateTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final Instant START = NOW.plusSeconds(86_400);
    private static final Instant END = START.plusSeconds(10_800);

    private static Event draft() {
        return Event.draft("Concert", "desc", "Main Hall", "Organizer", null, START, END, NOW);
    }

    @Nested
    class Drafting {

        @Test
        void draftStartsUnpublishedAndDeletable() {
            Event e = draft();

            assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
            assertThat(e.isOnSale()).isFalse();
            assertThat(e.isDeletable()).isTrue();
            assertThat(e.getTitle()).isEqualTo("Concert");
        }

        @Test
        void aBlankTitleIsRejected() {
            assertThatThrownBy(() -> Event.draft("  ", null, null, null, null, START, END, NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Title is required");
        }

        @Test
        void anEventCannotEndBeforeItStarts() {
            assertThatThrownBy(() -> Event.draft("Concert", null, null, null, null, END, START, NOW))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("endTime must be after startTime");
            assertThatThrownBy(() -> draft().reschedule(END, START))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        void titlesAreTrimmedOnDescribe() {
            Event e = draft();
            e.describe("  Spaced  ", null, null, null, null);
            assertThat(e.getTitle()).isEqualTo("Spaced");
        }
    }

    @Nested
    class Publication {

        @Test
        void publishPutsTheEventOnSale() {
            Event e = draft();

            e.publish(120);

            assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
            assertThat(e.isOnSale()).isTrue();
        }

        @Test
        void anEventWithNoSeatsCannotBePublished() {
            assertThatThrownBy(() -> draft().publish(0))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no seats");
        }

        /** A published event must be cancelled or completed first — deleting it cascades orders away. */
        @Test
        void aPublishedEventIsNotDeletable() {
            Event e = draft();
            e.publish(1);

            assertThat(e.isDeletable()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "COMPLETED"})
        void everyOtherStateIsDeletable(EventStatus status) {
            assertThat(CatalogFixtures.event(1L, status).isDeletable()).isTrue();
        }
    }

    @Nested
    class RevertingToDraft {

        @Test
        void revertIsAllowedWhileNothingHasSold() {
            Event e = draft();
            e.publish(10);

            e.revertToDraft(false);

            assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
        }

        @Test
        void revertIsRefusedOnceTicketsAreOut() {
            Event e = draft();
            e.publish(10);

            assertThatThrownBy(() -> e.revertToDraft(true))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already has issued tickets");
            assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        }
    }

    @Nested
    class StatusRouting {

        /** {@code changeStatusTo} must apply each target's own guard, not just assign the field. */
        @Test
        void changeStatusToRoutesThroughTheGuardForEachTarget() {
            assertThatThrownBy(() -> draft().changeStatusTo(EventStatus.PUBLISHED, 0, false))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no seats");

            assertThatThrownBy(() -> draft().changeStatusTo(EventStatus.DRAFT, 10, true))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("already has issued tickets");

            Event cancelled = draft();
            cancelled.changeStatusTo(EventStatus.CANCELLED, 0, true);
            assertThat(cancelled.getStatus()).isEqualTo(EventStatus.CANCELLED);

            Event completed = draft();
            completed.changeStatusTo(EventStatus.COMPLETED, 0, true);
            assertThat(completed.getStatus()).isEqualTo(EventStatus.COMPLETED);
        }

        @Test
        void cancelAndCompleteAreUnconditional() {
            Event e = draft();
            e.publish(1);
            assertThatNoException().isThrownBy(e::cancel);
            assertThat(e.getStatus()).isEqualTo(EventStatus.CANCELLED);
        }
    }

    @Nested
    class Categories {

        @Test
        void categoriseReplacesTheSetAndTreatsNullAsEmpty() {
            Event e = draft();

            e.categorise(Set.of(EventCategory.named("Music")));
            assertThat(e.getCategoryNames()).containsExactly("Music");

            e.categorise(null);
            assertThat(e.getCategoryNames()).isEmpty();
        }

        /** Defensive copy: mutating the caller's set must not reach inside the aggregate. */
        @Test
        void categoriseCopiesTheCallersSet() {
            Event e = draft();
            var mutable = new java.util.HashSet<EventCategory>();
            mutable.add(EventCategory.named("Music"));

            e.categorise(mutable);
            mutable.add(EventCategory.named("Sports"));

            assertThat(e.getCategoryNames()).containsExactly("Music");
        }
    }

    @Nested
    class TicketTypes {

        @Test
        void createStartsWithNothingSold() {
            TicketType tt = TicketType.create(1L, "VIP", new BigDecimal("500000"), 100);

            assertThat(tt.getQuantity()).isEqualTo(100);
            assertThat(tt.getSoldQuantity()).isZero();
        }

        @Test
        void addCapacityExtendsTheAllocation() {
            TicketType tt = TicketType.create(1L, "VIP", new BigDecimal("500000"), 100);

            tt.addCapacity(50);

            assertThat(tt.getQuantity()).isEqualTo(150);
        }

        @Test
        void capacityCannotBeRemovedByAddingANegativeAmount() {
            TicketType tt = TicketType.create(1L, "VIP", new BigDecimal("500000"), 100);

            assertThatThrownBy(() -> tt.addCapacity(-10))
                    .isInstanceOf(DomainException.class);
            assertThat(tt.getQuantity()).isEqualTo(100);
        }
    }
}
