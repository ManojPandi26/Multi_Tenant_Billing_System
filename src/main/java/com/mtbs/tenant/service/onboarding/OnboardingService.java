package com.mtbs.tenant.service.onboarding;

import com.mtbs.tenant.dto.onboarding.OnboardingStatusResponse;
import com.mtbs.tenant.dto.onboarding.OnboardingStep1Request;
import com.mtbs.tenant.dto.onboarding.OnboardingStep2Request;
import com.mtbs.tenant.dto.onboarding.OnboardingStep3Request;
import com.mtbs.tenant.dto.onboarding.OnboardingStepResponse;
import com.mtbs.tenant.entity.TenantOnboarding;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.tenant.entity.Tenant;
import com.mtbs.shared.enums.auth.Status;
import com.mtbs.tenant.enums.KycStatus;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.shared.exception.TenantException;
import com.mtbs.tenant.repository.PlanRepository;
import com.mtbs.tenant.repository.TenantOnboardingRepository;
import com.mtbs.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the 3-step tenant onboarding flow.
 *
 * ALL operations here are in the PUBLIC schema — TenantContext is NOT set
 * until step 3 completion when the subscription is created in the tenant schema.
 *
 * Write operations are delegated to OnboardingScopedService to keep
 * @Transactional within a proper Spring proxy boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final TenantOnboardingRepository onboardingRepository;
    private final PlanRepository planRepository;
    private final OnboardingScopedService onboardingScopedService;
    private final OnboardingCompletionService onboardingCompletionService;

    // ── Step 1 ────────────────────────────────────────────────────────────────

    public OnboardingStepResponse saveStep1(Long tenantId, OnboardingStep1Request request) {
        log.info("Saving onboarding step 1 for tenantId={}", tenantId);

        Tenant tenant = requireOnboardingTenant(tenantId);

        // Slug uniqueness check — exclude the tenant's own record
        TenantOnboarding existing = requireOnboardingRecord(tenantId);
        if (!request.getSlug().equals(existing.getSlug())) {
            if (onboardingRepository.existsBySlugAndTenantIdNot(request.getSlug(), tenantId)) {
                throw TenantException.slugAlreadyTaken(request.getSlug());
            }
        }

        onboardingScopedService.persistStep1(existing, request, tenant);

        return OnboardingStepResponse.builder()
                .stepCompleted(1)
                .nextStep(2)
                .onboardingComplete(false)
                .message("Business details saved successfully")
                .build();
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    public OnboardingStepResponse saveStep2(Long tenantId, OnboardingStep2Request request) {
        log.info("Saving onboarding step 2 (KYC) for tenantId={}", tenantId);

        requireOnboardingTenant(tenantId);
        TenantOnboarding onboarding = requireOnboardingRecord(tenantId);

        if (onboarding.getCompanyName() == null) {
            throw TenantException.stepOutOfOrder(1, 2);
        }

        onboardingScopedService.persistStep2(onboarding, request);

        return OnboardingStepResponse.builder()
                .stepCompleted(2)
                .nextStep(3)
                .onboardingComplete(false)
                .message("KYC details submitted successfully")
                .build();
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    public OnboardingStepResponse saveStep3(Long tenantId, OnboardingStep3Request request) {
        log.info("Saving onboarding step 3 (plan) for tenantId={}", tenantId);

        requireOnboardingTenant(tenantId);
        TenantOnboarding onboarding = requireOnboardingRecord(tenantId);

        if (onboarding.getKycStatus() == KycStatus.PENDING) {
            throw TenantException.stepOutOfOrder(2, 3);
        }

        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> ResourceException.notFound("Plan", request.getPlanId()));

        onboardingScopedService.persistStep3(onboarding, request);

        // Trigger completion — provisions subscription in tenant schema
        onboardingCompletionService.completeOnboarding(tenantId, plan, request.getBillingCycle());

        return OnboardingStepResponse.builder()
                .stepCompleted(3)
                .nextStep(0)
                .onboardingComplete(true)
                .message("Onboarding complete. Welcome!")
                .build();
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public OnboardingStatusResponse getStatus(Long tenantId) {
        log.info("Fetching onboarding status for tenantId={}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> ResourceException.notFound("Tenant", tenantId));

        TenantOnboarding onboarding = requireOnboardingRecord(tenantId);

        List<Integer> completed = resolveCompletedSteps(onboarding);
        boolean done = tenant.getStatus() == Status.ACTIVE;

        OnboardingStatusResponse.Step1Data step1 = null;
        if (onboarding.getCompanyName() != null) {
            step1 = OnboardingStatusResponse.Step1Data.builder()
                    .companyName(onboarding.getCompanyName())
                    .slug(onboarding.getSlug())
                    .industry(onboarding.getIndustry())
                    .phone(onboarding.getPhone())
                    .timezone(onboarding.getTimezone())
                    .teamSize(onboarding.getTeamSize())
                    .useCase(onboarding.getUseCase())
                    .build();
        }

        OnboardingStatusResponse.Step2Summary step2 = null;
        if (onboarding.getBusinessType() != null) {
            step2 = OnboardingStatusResponse.Step2Summary.builder()
                    .businessType(onboarding.getBusinessType().name())
                    .registrationNumber(onboarding.getRegistrationNumber())
                    .kycStatus(onboarding.getKycStatus())
                    .build();
        }

        OnboardingStatusResponse.Step3Summary step3 = null;
        if (onboarding.getSelectedPlanId() != null) {
            String planName = planRepository.findById(onboarding.getSelectedPlanId())
                    .map(Plan::getName).orElse("UNKNOWN");
            step3 = OnboardingStatusResponse.Step3Summary.builder()
                    .planId(onboarding.getSelectedPlanId())
                    .planName(planName)
                    .billingCycle(onboarding.getSelectedBillingCycle() != null
                            ? onboarding.getSelectedBillingCycle().name() : null)
                    .completedAt(onboarding.getCompletedAt())
                    .build();
        }

        return OnboardingStatusResponse.builder()
                .tenantId(tenantId)
                .currentStep(tenant.getOnboardingStep())
                .onboardingComplete(done)
                .completedSteps(completed)
                .kycStatus(onboarding.getKycStatus())
                .step1(step1)
                .step2(step2)
                .step3(step3)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant requireOnboardingTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> ResourceException.notFound("Tenant", tenantId));
        if (tenant.getStatus() == Status.ACTIVE) {
            throw TenantException.notInOnboarding(tenantId);
        }
        return tenant;
    }

    private TenantOnboarding requireOnboardingRecord(Long tenantId) {
        return onboardingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> ResourceException.notFound("OnboardingRecord", tenantId));
    }

    private List<Integer> resolveCompletedSteps(TenantOnboarding o) {
        List<Integer> steps = new ArrayList<>();
        if (o.getCompanyName() != null)    steps.add(1);
        if (o.getBusinessType() != null)   steps.add(2);
        if (o.getSelectedPlanId() != null) steps.add(3);
        return steps;
    }
}