/**
 * Audit: the {@code @Auditable} audit trail.
 *
 * <p>Owns {@code AuditLog} and its repository (in {@code …internal}), the {@code AuditAspect} that
 * writes a row for every {@code shared::audit} {@code @Auditable} service call, and
 * {@code AdminAuditController}.
 *
 * <p>Publishes no named interface. The aspect matches {@code @Auditable} purely via AOP, so audit
 * carries no compile-time dependency on the modules it observes — only the kernel's audit marker and
 * the security facet it reads the acting user from.
 */
@ApplicationModule(
        displayName = "Audit",
        allowedDependencies = {"shared::audit", "shared::security"})
package com.odoomaster.ticketing.audit;

import org.springframework.modulith.ApplicationModule;
