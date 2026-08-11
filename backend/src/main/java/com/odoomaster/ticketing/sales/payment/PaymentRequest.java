package com.odoomaster.ticketing.sales.payment;

import java.math.BigDecimal;

/**
 * Immutable request passed to a {@link PaymentGateway} when charging an order.
 *
 * @param orderId the order being paid
 * @param provider the requested provider/method (e.g. {@code MOMO}, {@code VNPAY}, {@code MOCK})
 * @param amount the amount to charge
 */
public record PaymentRequest(Long orderId, PaymentMethod provider, BigDecimal amount) {
}
