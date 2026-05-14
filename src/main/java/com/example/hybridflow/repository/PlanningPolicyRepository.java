package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.PlanningPolicy;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanningPolicyRepository extends JpaRepository<PlanningPolicy, Long> {

    // Used by PlanningPolicyService.getPoliciesByCompany — returns all, newest first
    List<PlanningPolicy> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    // to get the active policy of a company,,, usually, we only want the most recent
    // one
    Optional<PlanningPolicy> findFirstByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<PlanningPolicy> findByIdAndCompanyId(Long id, Long companyId);
}