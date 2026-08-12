package com.odoomaster.ticketing.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the Payment aggregate.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
  long countByStatus(com.odoomaster.ticketing.sales.payment.PaymentStatus status);

  java.util.List<Payment> findByOrderId(Long orderId);
}
