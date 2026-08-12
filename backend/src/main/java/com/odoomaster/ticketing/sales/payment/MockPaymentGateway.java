package com.odoomaster.ticketing.sales.payment;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Catch-all {@link PaymentGateway} that simulates a payment provider: it accepts every
 * provider and always reports success with a generated {@code MOCK-XXXX} transaction id.
 *
 * <p>It is ordered {@link Ordered#LOWEST_PRECEDENCE} so that, once real provider gateways are
 * added, {@link PaymentGatewayResolver} prefers a matching specific gateway and falls back to
 * this mock only when none applies. This preserves the system's current always-succeed
 * behaviour while leaving a clean extension point.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MockPaymentGateway implements PaymentGateway {

    /** {@inheritDoc} The mock supports any provider (it is the universal fallback). */
    @Override
    public boolean supports(PaymentMethod provider) {
        return true;
    }

    /** {@inheritDoc} Always succeeds with a {@code MOCK-} prefixed transaction id. */
    @Override
    public PaymentResult charge(PaymentRequest request) {
        String transactionId = "MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        return new PaymentResult(true, transactionId, PaymentStatus.SUCCEEDED, request.provider(), null);
    }
}
