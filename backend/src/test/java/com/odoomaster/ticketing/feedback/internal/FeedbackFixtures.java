package com.odoomaster.ticketing.feedback.internal;

import java.time.Instant;

/** Builders for feedback aggregates. See {@code CatalogFixtures} for why this lives here. */
public final class FeedbackFixtures {

    private FeedbackFixtures() {
    }

    public static Feedback feedback(Long id, FeedbackStatus status) {
        return feedback(id, status, FeedbackCategory.GENERAL, null);
    }

    public static Feedback feedback(Long id, FeedbackStatus status, FeedbackCategory category,
                                    Integer rating) {
        Instant resolvedAt = status == FeedbackStatus.RESOLVED ? Instant.now() : null;
        return new Feedback(id, 1L, null, category, "subject", "body", rating, status,
                Instant.now(), resolvedAt, null);
    }

    /** See {@code SalesFixtures.withId}. */
    public static <T> T withId(T entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign id to " + entity.getClass().getSimpleName(), e);
        }
    }
}
