package com.odoomaster.ticketing.sales.payment;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory that selects the {@link PaymentGateway} {@link PaymentGateway#supports(PaymentMethod) supporting}
 * a requested provider.
 *
 * <p>Spring injects all {@code PaymentGateway} beans ordered by {@code @Order}, so the resolver
 * returns the first specific gateway that claims the provider and falls back to
 * {@link MockPaymentGateway} (lowest precedence) when none does. This is the factory half of the
 * Strategy + Factory pairing that keeps {@code OrderService} decoupled from concrete providers.
 */
@Component
public class PaymentGatewayResolver {

    private final List<PaymentGateway> gateways;
    private final PaymentGateway fallback;

    /**
     * @param gateways all registered gateways, injected in {@code @Order} sequence
     */
    public PaymentGatewayResolver(List<PaymentGateway> gateways) {
        this.gateways = gateways;
        this.fallback = gateways.stream()
                .filter(MockPaymentGateway.class::isInstance)
                .map(PaymentGateway.class::cast)
                .findFirst()
                .orElse(gateways.isEmpty() ? null : gateways.get(gateways.size() - 1));
    }

    /**
     * Resolve the gateway to use for a provider.
     *
     * @param provider the requested provider/method
     * @return the first gateway that supports {@code provider}, or the mock fallback
     */
    public PaymentGateway resolve(PaymentMethod provider) {
        return gateways.stream()
                .filter(g -> g.supports(provider))
                .findFirst()
                .orElse(fallback);
    }
}
