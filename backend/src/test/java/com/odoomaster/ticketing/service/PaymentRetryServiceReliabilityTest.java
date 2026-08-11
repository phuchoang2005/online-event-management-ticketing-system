package com.odoomaster.ticketing.service;
import com.odoomaster.ticketing.sales.PaymentRetryService;

import com.odoomaster.ticketing.sales.internal.PaymentRetry;
import com.odoomaster.ticketing.sales.internal.PaymentRetryStatus;
import com.odoomaster.ticketing.sales.internal.PaymentRetryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRetryServiceReliabilityTest {

    @Mock PaymentRetryRepository retries;

    @ParameterizedTest
    @CsvSource({
            "0,FAILED,TIMEOUT,1",
            "1,FAILED,GATEWAY_500,2",
            "2,SUCCEEDED,,3",
            "9,FAILED,RATE_LIMITED,10"
    })
    void recordAttempt_givenExistingAttempts_incrementsAttemptNumber(long existing, PaymentRetryStatus status, String errorCode, int expected) {
        PaymentRetryService service = new PaymentRetryService(retries);
        when(retries.countByPaymentId(7L)).thenReturn(existing);
        when(retries.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentRetry retry = service.recordAttempt(7L, status, errorCode);

        assertThat(retry.getPaymentId()).isEqualTo(7L);
        assertThat(retry.getStatus()).isEqualTo(status);
        assertThat(retry.getErrorCode()).isEqualTo(errorCode);
        assertThat(retry.getAttemptNo()).isEqualTo(expected);
    }

    @Test
    void recordAttempt_capturesSavedRetryPayload() {
        PaymentRetryService service = new PaymentRetryService(retries);
        when(retries.countByPaymentId(7L)).thenReturn(4L);
        when(retries.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordAttempt(7L, PaymentRetryStatus.FAILED, "TIMEOUT");

        ArgumentCaptor<PaymentRetry> saved = ArgumentCaptor.forClass(PaymentRetry.class);
        verify(retries).save(saved.capture());
        assertThat(saved.getValue().getAttemptNo()).isEqualTo(5);
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentRetryStatus.FAILED);
    }

    @Test
    void recordAttempt_concurrentRepositoryCounts_producesDistinctAttemptsWhenStoreIsAtomic() throws Exception {
        PaymentRetryService service = new PaymentRetryService(retries);
        AtomicLong count = new AtomicLong();
        var attempts = ConcurrentHashMap.<Integer>newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        when(retries.countByPaymentId(7L)).thenAnswer(inv -> count.getAndIncrement());
        when(retries.save(any())).thenAnswer(inv -> {
            PaymentRetry retry = inv.getArgument(0);
            attempts.add(retry.getAttemptNo());
            return retry;
        });

        var pool = Executors.newFixedThreadPool(6);
        var futures = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> pool.submit(() -> {
                    start.await(2, TimeUnit.SECONDS);
                    service.recordAttempt(7L, PaymentRetryStatus.FAILED, "E" + i);
                    return null;
                }))
                .toList();
        start.countDown();
        for (var future : futures) future.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(attempts).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList());
    }
}
