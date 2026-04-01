package com.mtbs.billing.repository;

import com.mtbs.billing.entity.Payment;
import com.mtbs.shared.enums.billing.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String key);

    Optional<Payment> findByRazorpayOrderId(String orderId);

    Optional<Payment> findByRazorpayPaymentId(String paymentId);

    List<Payment> findAllByStatusAndNextRetryAtBefore(PaymentStatus status, Instant now);

    List<Payment> findByInvoiceId(Long invoiceId);

    long countByStatus(PaymentStatus paymentStatus);
}
