/**
 * Notification: in-app notifications.
 *
 * <p>Owns {@code Notification} (in {@code …internal}), {@code NotificationService} and the
 * {@code NotificationEventListener} that reacts to the {@code shared::contracts}
 * {@code TicketsIssuedEvent}.
 *
 * <p>Publishes no named interface (its {@code NotificationController} serves the owner directly).
 * Depends on {@code iam::directory} (to resolve recipients in the seeder) plus the kernel facets
 * it uses; it is decoupled from {@code sales}/{@code ticketing} via the event contract.
 */
@ApplicationModule(
        displayName = "Notification",
        allowedDependencies = {
                "iam::directory",
                "shared::errors",
                "shared::security",
                "shared::contracts"})
package com.odoomaster.ticketing.notification;

import org.springframework.modulith.ApplicationModule;
