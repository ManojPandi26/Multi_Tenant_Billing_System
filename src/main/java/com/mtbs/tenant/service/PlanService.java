package com.mtbs.tenant.service;

import com.mtbs.tenant.dto.plan.CreatePlanRequest;
import com.mtbs.tenant.dto.plan.PlanResponse;
import com.mtbs.tenant.dto.plan.UpdatePlanRequest;
import com.mtbs.tenant.entity.Plan;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.tenant.mapper.PlanMapper;
import com.mtbs.tenant.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    public List<PlanResponse> getAllPublicPlans() {
        return planRepository.findAllByIsActiveTrueAndIsPublicTrue()
                .stream()
                .map(planMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<PlanResponse> getAllPlans() {
        return planRepository.findAll()
                .stream()
                .map(planMapper::toResponse)
                .collect(Collectors.toList());
    }

    public PlanResponse getPlanByIdAsResponse(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> ResourceException.notFound("Plan", id));
        return planMapper.toResponse(plan);
    }

    public Plan getPlanById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> ResourceException.notFound("Plan", id));
    }

    public Plan getPlanByName(String name) {
        return planRepository.findByName(name)
                .orElseThrow(() -> ResourceException.notFound("Plan", name));
    }

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request) {
        if (planRepository.existsByName(request.getName())) {
            throw ResourceException.alreadyExists("Plan", request.getName());
        }

        Plan plan = planMapper.toEntity(request);
        Plan saved = planRepository.save(plan);
        log.info("Created plan: {}", saved.getName());
        return planMapper.toResponse(saved);
    }

    @Transactional
    public PlanResponse updatePlan(Long id, UpdatePlanRequest request) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> ResourceException.notFound("Plan", id));

        if (request.getDisplayName() != null) plan.setDisplayName(request.getDisplayName());
        if (request.getDescription() != null) plan.setDescription(request.getDescription());
        if (request.getPriceMonthly() != null) plan.setPriceMonthly(request.getPriceMonthly());
        if (request.getPriceAnnual() != null) plan.setPriceAnnual(request.getPriceAnnual());
        if (request.getCurrency() != null) plan.setCurrency(request.getCurrency());
        if (request.getTrialDays() != null) plan.setTrialDays(request.getTrialDays());
        if (request.getMaxUsers() != null) plan.setMaxUsers(request.getMaxUsers());
        if (request.getMaxApiCallsPerMonth() != null) plan.setMaxApiCallsPerMonth(request.getMaxApiCallsPerMonth());
        if (request.getMaxStorageGb() != null) plan.setMaxStorageGb(request.getMaxStorageGb());
        if (request.getIsActive() != null) plan.setIsActive(request.getIsActive());
        if (request.getIsPublic() != null) plan.setIsPublic(request.getIsPublic());

        Plan saved = planRepository.save(plan);
        log.info("Updated plan: {}", saved.getName());
        return planMapper.toResponse(saved);
    }

    @Transactional
    public void deactivatePlan(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> ResourceException.notFound("Plan", id));
        plan.setIsActive(false);
        plan.setDeleted(true);
        plan.setDeletedAt(Instant.now());
        planRepository.save(plan);
        log.info("Deactivated plan: {}", plan.getName());
    }
}
