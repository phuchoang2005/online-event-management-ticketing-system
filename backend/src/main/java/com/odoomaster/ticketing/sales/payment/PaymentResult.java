package com.odoomaster.ticketing.sales.payment;

/**
 * Outcome returned by a {@link PaymentGateway#charge(PaymentRequest)} call.
 *
 * @param success whether the charge succeeded. Callers <strong>must</strong> branch on this —
 *        {@code OrderService} ignored it before ADR-0013, which is why no payment could ever fail
 * @param transactionId the gateway transaction reference to persist
 * @param status the persisted payment status
 * @param provider the provider that handled the charge
 * @param errorCode the gateway's decline reason, or {@code null} on success
 */
public record PaymentResult(boolean success, String transactionId, PaymentStatus status,
                            PaymentMethod provider, String errorCode) {
}
