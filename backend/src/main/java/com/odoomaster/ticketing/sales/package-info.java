/**
 * Sales: orders, payments and the mock payment-gateway retry loop.
 *
 * <p>Owns {@code Order}, {@code OrderItem}, {@code Payment}, {@code PaymentRetry} (in
 * {@code …internal}), the concurrency-critical {@code OrderService} (single-transaction
 * lock→sell→issue flow) and {@code PaymentRetryService}.
 *
 * <p>Publishes {@code sales::reporting} ({@code SalesReporting} + {@code DailyRevenue} —
 * revenue/order aggregates for {@code analytics}). {@code OrderService} itself is deliberately not
 * published: ordering is driven through sales' own controller only.
 *
 * <p>Depends on {@code catalog::events}/{@code catalog::inventory} and
 * {@code ticketing::issuance} — all invoked inside {@code OrderService.pay()}'s one transaction so
 * ACID/seat-lock semantics are unchanged. Note it does <em>not</em> get
 * {@code ticketing::reporting}: issuing tickets and reporting on them are separate facets. Purges
 * its rows on the {@code shared::contracts} {@code EventDeletedEvent} cascade.
 */
@ApplicationModule(
        displayName = "Sales",
        allowedDependencies = {
                "catalog::events",
                "catalog::inventory",
                "ticketing::issuance",
                "shared::errors",
                "shared::security",
                "shared::audit",
                "shared::contracts"})
package com.odoomaster.ticketing.sales;

import org.springframework.modulith.ApplicationModule;
