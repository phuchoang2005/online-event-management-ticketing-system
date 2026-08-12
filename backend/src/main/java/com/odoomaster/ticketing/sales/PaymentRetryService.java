package com.odoomaster.ticketing.sales;

import com.odoomaster.ticketing.sales.internal.PaymentRetry;
import com.odoomaster.ticketing.sales.internal.PaymentRetryRepository;
import com.odoomaster.ticketing.sales.internal.PaymentRetryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Records payment retry attempts (attempt number + error code) for analytics and audit.
 *
 * <p>Called by {@code OrderService.pay()} whenever a gateway declines. Until ADR-0013 this class had
 * no callers at all: {@code PaymentResult.success()} was never read, so there was no failure branch
 * to record anything from.
 *
 * <p>{@code Propagation.MANDATORY} is deliberate — an attempt only means something as part of the
 * payment it belongs to, and a decline rolls that transaction back together with the seat and order
 * changes that preceded it.
 */
@Service
public class PaymentRetryService {

    private final PaymentRetryRepository retries;
    private final Clock clock;

    public PaymentRetryService(PaymentRetryRepository retries, Clock clock) {
        this.retries = retries;
        this.clock = clock;
    }

    /**
     * Append the next numbered attempt for a payment.
     *
     * @param errorCode the gateway's decline reason, or {@code null} on success
     */
    @Transactional
    public PaymentRetry recordAttempt(Long paymentId, PaymentRetryStatus status, String errorCode) {
        int next = PaymentRetry.nextAttemptNo(retries.countByPaymentId(paymentId));
        return retries.save(PaymentRetry.attempt(paymentId, status, next, errorCode, clock.instant()));
    }
}
