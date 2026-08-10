/**
 * Feedback: post-event ratings and reviews.
 *
 * <p>Owns {@code Feedback} and its repository (in {@code …internal}) and {@code FeedbackService}.
 *
 * <p>Publishes no named interface. Depends on {@code catalog::events} (to validate the event) and
 * {@code iam::directory} (to resolve the reviewer), plus the kernel's error and security facets.
 * It has no reason to touch {@code catalog::inventory}, and now cannot.
 */
@ApplicationModule(
        displayName = "Feedback",
        allowedDependencies = {
                "catalog::events",
                "iam::directory",
                "shared::errors",
                "shared::security"})
package com.odoomaster.ticketing.feedback;

import org.springframework.modulith.ApplicationModule;
