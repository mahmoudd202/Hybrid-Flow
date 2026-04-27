package com.example.hybridflow.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.RequestResponseDTO;
import com.example.hybridflow.dto.RequestSubmissionDTO;
import com.example.hybridflow.entity.Request;
import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.RequestRepository;

import java.util.List;

@Service
public class RequestService {
    private final RequestRepository requestRepository;

    public RequestService(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Transactional
    public RequestResponseDTO createRequest(RequestSubmissionDTO dto, User currentUser) {
        validateAuthenticatedUser(currentUser);

        // Date validation: endDate must be >= startDate
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessValidationException("endDate must be on or after startDate");
        }

        // Duplicate check: same user, same type, overlapping dates, still PENDING
        List<Request> existingPending = requestRepository.findByRequesterIdAndStatusAndType(
                currentUser.getId(), RequestStatus.PENDING, dto.getType());

        for (Request existing : existingPending) {
            boolean overlaps = !dto.getEndDate().isBefore(existing.getStartDate())
                    && !dto.getStartDate().isAfter(existing.getEndDate());
            if (overlaps) {
                throw new BusinessValidationException(
                        "You already have a pending " + dto.getType() + " request overlapping these dates"
                );
            }
        }

        Request request = new Request();
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setReason(dto.getReason());
        request.setRequester(currentUser);
        request.setCompany(currentUser.getCompany());
        request.setStatus(RequestStatus.PENDING);

        Request saved = requestRepository.save(request);
        return toResponse(saved);
    }

    public List<RequestResponseDTO> getRequestsByUser(User currentUser) {
        validateAuthenticatedUser(currentUser);

        return requestRepository.findByRequesterId(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteRequest(Long requestId, User currentUser) {
        validateAuthenticatedUser(currentUser);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!request.getRequester().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only delete your own requests");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessValidationException("You can only delete pending requests. Please contact HR to cancel an already processed request.");
        }

        requestRepository.delete(request);
    }

    public List<RequestResponseDTO> getPendingRequestsByCompany(User hrUser) {
        validateHrContext(hrUser);

        return requestRepository.findByCompanyIdAndStatus(
                        hrUser.getCompany().getId(), RequestStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RequestResponseDTO updateRequestStatus(Long requestId, RequestStatus newStatus, User hrUser) {
        validateHrContext(hrUser);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        // Ensure HR is from the same company as the request
        if (!request.getCompany().getId().equals(hrUser.getCompany().getId())) {
            throw new AccessDeniedException("You cannot handle requests for another company");
        }

        // Can only act on PENDING requests
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessValidationException(
                    "This request has already been " + request.getStatus().name().toLowerCase()
            );
        }

        request.setStatus(newStatus);
        request.setHandledBy(hrUser);

        Request saved = requestRepository.save(request);
        return toResponse(saved);
    }

    // ────────────────────────────────────────────────────────────────
    //  VALIDATION HELPERS
    // ────────────────────────────────────────────────────────────────

    private void validateAuthenticatedUser(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (user.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }
    }

    private void validateHrContext(User hrUser) {
        if (hrUser == null || hrUser.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (hrUser.getCompany() == null) {
            throw new AccessDeniedException("HR is not attached to a company");
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  DTO CONVERSION
    // ────────────────────────────────────────────────────────────────

    private RequestResponseDTO toResponse(Request request) {
        return RequestResponseDTO.builder()
                .id(request.getId())
                .type(request.getType())
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .createdAt(request.getCreatedAt())
                .requesterId(request.getRequester().getId())
                .requesterEmail(request.getRequester().getEmail())
                .companyId(request.getCompany().getId())
                .handledById(request.getHandledBy() != null ? request.getHandledBy().getId() : null)
                .handledByEmail(request.getHandledBy() != null ? request.getHandledBy().getEmail() : null)
                .build();
    }
}