package com.example.hybridflow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.PlanningPolicyRequestDTO;
import com.example.hybridflow.dto.PlanningPolicyResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.PlanningPolicy;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.PlanningPolicyRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanningPolicyService {

    private final PlanningPolicyRepository planningPolicyRepository;

    @Transactional
    public PlanningPolicyResponseDTO createPolicy(PlanningPolicyRequestDTO dto, User currentUser) {
        Company company = validateHrContext(currentUser);

        validatePolicyConstraints(dto);

        PlanningPolicy policy = new PlanningPolicy();
        policy.setCompany(company);
        mapDtoToEntity(dto, policy);

        return toResponse(planningPolicyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public List<PlanningPolicyResponseDTO> getPoliciesByCompany(User currentUser) {
        Company company = validateHrContext(currentUser);

        return planningPolicyRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanningPolicyResponseDTO getPolicyById(Long policyId, User currentUser) {
        Company company = validateHrContext(currentUser);

        PlanningPolicy policy = findAndVerifyOwnership(policyId, company);
        return toResponse(policy);
    }

    @Transactional
    public PlanningPolicyResponseDTO updatePolicy(Long policyId, PlanningPolicyRequestDTO dto, User currentUser) {
        Company company = validateHrContext(currentUser);

        validatePolicyConstraints(dto);

        PlanningPolicy policy = findAndVerifyOwnership(policyId, company);
        mapDtoToEntity(dto, policy);

        return toResponse(planningPolicyRepository.save(policy));
    }

    @Transactional
    public void deletePolicy(Long policyId, User currentUser) {
        Company company = validateHrContext(currentUser);

        PlanningPolicy policy = findAndVerifyOwnership(policyId, company);
        planningPolicyRepository.delete(policy);
    }

    private Company validateHrContext(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (user.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR can manage planning policies");
        }
        if (user.getCompany() == null) {
            throw new AccessDeniedException("HR user is not assigned to a company");
        }
        return user.getCompany();
    }

    private PlanningPolicy findAndVerifyOwnership(Long policyId, Company company) {
        PlanningPolicy policy = planningPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Planning policy not found with id: " + policyId));

        if (!policy.getCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("This planning policy does not belong to your company");
        }
        return policy;
    }

    private void validatePolicyConstraints(PlanningPolicyRequestDTO dto) {
        if (dto.getMinOfficeDaysPerWeek() > dto.getMaxOfficeDaysPerWeek()) {
            throw new BusinessValidationException(
                    "minOfficeDaysPerWeek cannot be greater than maxOfficeDaysPerWeek");
        }
        if (dto.getMaxOfficeDaysPerWeek() > dto.getWorkingDaysPerWeek()) {
            throw new BusinessValidationException(
                    "maxOfficeDaysPerWeek cannot exceed workingDaysPerWeek");
        }
        if (dto.getMinTeamSharedDays() > dto.getWorkingDaysPerWeek()) {
            throw new BusinessValidationException(
                    "minTeamSharedDays cannot exceed workingDaysPerWeek");
        }
        if (dto.getMaxConsecutiveOfficeDays() > dto.getWorkingDaysPerWeek()) {
            throw new BusinessValidationException(
                    "maxConsecutiveOfficeDays cannot exceed workingDaysPerWeek");
        }
    }

    private void mapDtoToEntity(PlanningPolicyRequestDTO dto, PlanningPolicy policy) {
        policy.setName(dto.getName().trim());
        policy.setWorkingDaysPerWeek(dto.getWorkingDaysPerWeek());
        policy.setMinOfficeDaysPerWeek(dto.getMinOfficeDaysPerWeek());
        policy.setMaxOfficeDaysPerWeek(dto.getMaxOfficeDaysPerWeek());
        policy.setMaxConsecutiveOfficeDays(dto.getMaxConsecutiveOfficeDays());
        policy.setMinTeamSharedDays(dto.getMinTeamSharedDays());
        policy.setCoPresenceThresholdPercentagePerDay(dto.getCoPresenceThresholdPercentagePerDay());
    }

    private PlanningPolicyResponseDTO toResponse(PlanningPolicy policy) {
        return PlanningPolicyResponseDTO.builder()
                .id(policy.getId())
                .companyId(policy.getCompany().getId())
                .name(policy.getName())
                .workingDaysPerWeek(policy.getWorkingDaysPerWeek())
                .minOfficeDaysPerWeek(policy.getMinOfficeDaysPerWeek())
                .maxOfficeDaysPerWeek(policy.getMaxOfficeDaysPerWeek())
                .maxConsecutiveOfficeDays(policy.getMaxConsecutiveOfficeDays())
                .minTeamSharedDays(policy.getMinTeamSharedDays())
                .coPresenceThresholdPercentagePerDay(policy.getCoPresenceThresholdPercentagePerDay())
                .createdAt(policy.getCreatedAt())
                .build();
    }
}