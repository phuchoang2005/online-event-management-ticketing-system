package com.odoomaster.ticketing.sales.payment;

/**
 * Strategy abstraction over a payment provider.
 *
 * <p>This is the <strong>Strategy</strong> pattern: {@code OrderService} depends on this
 * interface rather than a concrete provider, so new gateways (MoMo, VNPay, Stripe, …) can be
 * added as additional implementations without touching the order/payment flow. The concrete
 * strategy for a given order is chosen at runtime by {@link PaymentGatewayResolver} (a simple
 * factory) based on the requested provider.
 *
 * <p>Today only {@link MockPaymentGateway} exists; it accepts every provider and always
 * succeeds, matching the project's simulated-payment behaviour.
 */
public interface PaymentGateway {

    /**
     * Whether this gateway can handle the given provider/method.
     *
     * @param provider the requested provider
     * @return {@code true} if this gateway should be used for that provider
     */
    boolean supports(PaymentMethod provider);

    /**
     * Attempt to charge the order described by {@code request}.
     *
     * @param request the order id, provider and amount to charge
     * @return the charge outcome (transaction id + status) to persist
     */
    PaymentResult charge(PaymentRequest request);
}
