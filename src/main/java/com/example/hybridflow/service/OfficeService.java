package com.example.hybridflow.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.FirstOfficeResponseDTO;
import com.example.hybridflow.dto.OfficeCreateRequestDTO;
import com.example.hybridflow.dto.OfficeResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeService {

    private final OfficeRepository officeRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public List<OfficeResponseDTO> getByCompany(User currentUser) {
        Company company = validateHrCompanyContext(currentUser);

        return officeRepository.findByCompanyIdOrderByNameAsc(company.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FirstOfficeResponseDTO getFirstByCompany(User currentUser) {
        Company company = validateHrCompanyContext(currentUser);

        return officeRepository.findFirstByCompanyIdOrderByIdAsc(company.getId())
                .map(office -> FirstOfficeResponseDTO.builder()
                        .hasOffice(true)
                        .office(toResponse(office))
                        .build())
                .orElseGet(() -> FirstOfficeResponseDTO.builder()
                        .hasOffice(false)
                        .office(null)
                        .build());
    }

    @Transactional
    public OfficeResponseDTO createOffice(OfficeCreateRequestDTO dto, User currentUser) {
        Company company = validateHrCompanyContext(currentUser);

        String normalizedName = normalizeName(dto.getName());

        if (officeRepository.existsByNameAndCompanyId(normalizedName, company.getId())) {
            throw new BusinessValidationException(
                    "An office with the name '" + normalizedName + "' already exists in your company.");
        }

        Office office = new Office();
        office.setName(normalizedName);
        office.setMaxCapacity(dto.getMaxCapacity());
        office.setCompany(company);

        Office saved = officeRepository.save(office);
        return toResponse(saved);
    }

    @Transactional
    public OfficeResponseDTO updateOffice(Long officeId, OfficeCreateRequestDTO dto, User currentUser) {
        Company company = validateHrCompanyContext(currentUser);

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        if (!office.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You do not have access to this office.");
        }

        String normalizedName = normalizeName(dto.getName());

        officeRepository.findByNameAndCompanyId(normalizedName, company.getId())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(officeId)) {
                        throw new BusinessValidationException(
                                "An office with the name '" + normalizedName + "' already exists in your company.");
                    }
                });

        office.setName(normalizedName);
        office.setMaxCapacity(dto.getMaxCapacity());

        Office updated = officeRepository.save(office);
        return toResponse(updated);
    }

    @Transactional
    public void deleteOffice(Long officeId, User currentUser) {
        Company company = validateHrCompanyContext(currentUser);

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        if (!office.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You do not have access to this office.");
        }

        if (!teamRepository.findByOfficeId(officeId).isEmpty()) {
            throw new BusinessValidationException(
                    "Cannot delete office because it is currently assigned to one or more teams.");
        }

        officeRepository.delete(office);
    }

    private Company validateHrCompanyContext(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (user.getRole() != Role.HR && user.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only HR or managers can view company offices");
        }

        if (user.getCompany() == null) {
            throw new AccessDeniedException("HR user is not assigned to a company");
        }

        return user.getCompany();
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new BusinessValidationException("Office name is required");
        }

        String normalized = name.trim().replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            throw new BusinessValidationException("Office name is required");
        }

        return normalized;
    }

    private OfficeResponseDTO toResponse(Office office) {
        return OfficeResponseDTO.builder()
                .id(office.getId())
                .name(office.getName())
                .maxCapacity(office.getMaxCapacity())
                .companyId(office.getCompany().getId())
                .companyName(office.getCompany().getCompanyName())
                .build();
    }
}