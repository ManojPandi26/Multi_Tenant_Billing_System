package com.mtbs.tenant.entity;

import com.mtbs.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "plans")
@SQLDelete(sql = "UPDATE plans SET deleted = true WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_monthly", precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_annual", precision = 10, scale = 2)
    private BigDecimal priceAnnual;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "trial_days")
    private Integer trialDays;

    @Column(name = "max_users", nullable = false)
    @Builder.Default
    private Integer maxUsers = 3;

    @Column(name = "max_api_calls_per_month", nullable = false)
    @Builder.Default
    private Long maxApiCallsPerMonth = 1000L;

    @Column(name = "max_storage_gb", nullable = false)
    @Builder.Default
    private Integer maxStorageGb = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;
}