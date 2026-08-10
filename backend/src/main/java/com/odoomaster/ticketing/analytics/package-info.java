/**
 * Analytics: admin dashboards and cross-capability reporting.
 *
 * <p>Owns {@code AnalyticsService} — it composes figures from other modules' reporting facets and
 * holds no persistent state of its own.
 *
 * <p>Publishes no named interface. Depends on {@code catalog::events},
 * {@code sales::reporting} (revenue is composed here, not in {@code catalog}) and
 * {@code ticketing::reporting} — read-only facets only; it can reach neither
 * {@code catalog::inventory} nor {@code ticketing::issuance}. It uses no kernel type at all, so
 * it declares no {@code shared} facet.
 */
@ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {
                "catalog::events",
                "sales::reporting",
                "ticketing::reporting"})
package com.odoomaster.ticketing.analytics;

import org.springframework.modulith.ApplicationModule;
