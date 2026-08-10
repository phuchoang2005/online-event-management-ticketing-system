package com.odoomaster.ticketing.shared;

import org.springframework.modulith.NamedInterface;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful execution should write an {@code audit_logs} row.
 *
 * <p>Processed by the audit aspect (AOP), which records the acting user, the returned entity's
 * id, and the request trace id — keeping auditing out of the business logic.
 *
 * <p>Part of the {@code shared::audit} named interface.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@NamedInterface("audit")
public @interface Auditable {
    /** @return the audited action name (e.g. {@code ORDER_PAID}) */
    String action();

    /** @return the affected entity/table name (e.g. {@code orders}) */
    String entity();
}
