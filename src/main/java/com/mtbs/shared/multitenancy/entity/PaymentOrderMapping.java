package com.mtbs.shared.multitenancy.entity;

import com.mtbs.shared.entity.AuditableEntity;
import com.mtbs.shared.enums.billing.InvoiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_order_mapping", schema = "public")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderMapping extends AuditableEntity {

    @Column(name = "razorpay_order_id", unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_link_id", unique = true, length = 100)
    private String razorpayPaymentLinkId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "schema_name", nullable = false, length = 100)
    private String schemaName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String domain = "PLATFORM";

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", length = 30)
    private InvoiceType invoiceType;

    @Column(name = "invoice_id")
    private Long invoiceId;
}
