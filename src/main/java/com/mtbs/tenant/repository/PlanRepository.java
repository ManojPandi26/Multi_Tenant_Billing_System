package com.mtbs.tenant.repository;

import com.mtbs.tenant.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByName(String name);

    List<Plan> findAllByIsActiveTrue();

    List<Plan> findAllByIsActiveTrueAndIsPublicTrue();

    boolean existsByName(String name);
}
