package com.odoomaster.ticketing.sales;

import com.odoomaster.ticketing.sales.internal.PaymentRetry;
import com.odoomaster.ticketing.sales.internal.PaymentRetryStatus;
import com.odoomaster.ticketing.sales.internal.PaymentRetryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records payment retry attempts (attempt number + error code) for analytics and audit.
 */
@Service
public class PaymentRetryService {

    private final PaymentRetryRepository retries;

    public PaymentRetryService(PaymentRetryRepository retries) {
        this.retries = retries;
    }

    @Transactional
    public PaymentRetry recordAttempt(Long paymentId, PaymentRetryStatus status, String errorCode) {
        int next = (int) retries.countByPaymentId(paymentId) + 1;
        PaymentRetry r = PaymentRetry.builder()
                .paymentId(paymentId)
                .status(status)
                .attemptNo(next)
                .errorCode(errorCode)
                .build();
        return retries.save(r);
    }
}
