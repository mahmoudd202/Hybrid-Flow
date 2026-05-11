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
import com.example.hybridflow.repository.OfficeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeService {

    private final OfficeRepository officeRepository;

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

    private Company validateHrCompanyContext(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (user.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR can manage offices");
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