package com.mtbs.shared.multitenancy.repository;

import com.mtbs.shared.multitenancy.entity.PaymentOrderMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOrderMappingRepository extends JpaRepository<PaymentOrderMapping, Long> {

    Optional<PaymentOrderMapping> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentOrderMapping> findByRazorpayPaymentLinkId(String razorpayPaymentLinkId);

    Optional<PaymentOrderMapping> findByRazorpayPaymentId(String razorpayPaymentId);
}
