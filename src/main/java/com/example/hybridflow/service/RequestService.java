package com.example.hybridflow.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.RequestResponseDTO;
import com.example.hybridflow.dto.RequestSubmissionDTO;
import com.example.hybridflow.entity.Request;
import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.entity.RequestType;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.service.MeetingService;
import com.example.hybridflow.service.TaskService;
import com.example.hybridflow.service.ScheduleAvailabilityService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final ScheduleAvailabilityService scheduleAvailabilityService;
    private final MeetingService meetingService;
    private final TaskService taskService;

    public RequestService(
            RequestRepository requestRepository,
            ScheduleEntryRepository scheduleEntryRepository,
            ScheduleAvailabilityService scheduleAvailabilityService,
            MeetingService meetingService,
            TaskService taskService
    ) {
        this.requestRepository = requestRepository;
        this.scheduleEntryRepository = scheduleEntryRepository;
        this.scheduleAvailabilityService = scheduleAvailabilityService;
        this.meetingService = meetingService;
        this.taskService = taskService;
    }

    @Transactional
    public RequestResponseDTO createRequest(RequestSubmissionDTO dto, User currentUser) {
        validateAuthenticatedUser(currentUser);
        validateSubmission(dto);

        // Validate that the user has published schedule entries for the requested dates
        // and is not already OFF for those dates (unless it\'s a WFH request on an OFFICE day)
        LocalDate currentDate = dto.getStartDate();
        while (!currentDate.isAfter(dto.getEndDate())) {
            LocalDate finalCurrentDate = currentDate;
            scheduleEntryRepository.findPublishedEntryForUserOnDate(currentUser.getId(), finalCurrentDate)
                    .ifPresentOrElse(
                            entry -> {
                                if (dto.getType() == RequestType.WFH && entry.getWorkMode() != WorkMode.OFFICE) {
                                    throw new BusinessValidationException("Cannot request WFH on a day you are not scheduled to be in the office: " + finalCurrentDate);
                                } else if (dto.getType() == RequestType.PTO && entry.getWorkMode() == WorkMode.OFF) {
                                    throw new BusinessValidationException("Cannot request PTO on a day you are already OFF: " + finalCurrentDate);
                                }
                            },
                            () -> {
                                throw new BusinessValidationException("Cannot create request. You have no published schedule entry on " + finalCurrentDate);
                            }
                    );
            currentDate = currentDate.plusDays(1);
        }

        /*
         * Prevent conflicting requests.
         *
         * Example:
         * - User cannot request PTO and WFH for the same day.
         * - User cannot create a new request overlapping an already APPROVED request.
         * - REJECTED requests do not block new requests.
         */
        List<Request> existingRequests = requestRepository.findByRequesterId(currentUser.getId());

        for (Request existing : existingRequests) {
            if (existing.getStatus() == RequestStatus.REJECTED) {
                continue;
            }

            boolean overlaps = datesOverlap(
                    dto.getStartDate(),
                    dto.getEndDate(),
                    existing.getStartDate(),
                    existing.getEndDate()
            );

            if (overlaps) {
                throw new BusinessValidationException(
                        "You already have a " + existing.getStatus().name().toLowerCase()
                                + " " + existing.getType()
                                + " request overlapping these dates"
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

    @Transactional(readOnly = true)
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
            throw new BusinessValidationException(
                    "You can only delete pending requests. Please contact HR to cancel an already processed request."
            );
        }

        requestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public List<RequestResponseDTO> getPendingRequestsByCompany(User hrUser) {
        validateHrContext(hrUser);

        return requestRepository.findByCompanyIdAndStatus(
                        hrUser.getCompany().getId(),
                        RequestStatus.PENDING
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestResponseDTO> getRequestHistory(
            User hrUser,
            RequestStatus status,
            RequestType type,
            Long requesterId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateHrContext(hrUser);

        return requestRepository.findCompanyRequestHistoryWithFilters(
                hrUser.getCompany().getId(),
                status,
                type,
                requesterId,
                startDate,
                endDate
        ).stream()
         .map(this::toResponse)
         .toList();
    }

    @Transactional
    public RequestResponseDTO updateRequestStatus(Long requestId, RequestStatus newStatus, User hrUser) {
        validateHrContext(hrUser);

        if (newStatus == null) {
            throw new BusinessValidationException("Request status is required");
        }

        if (newStatus != RequestStatus.APPROVED && newStatus != RequestStatus.REJECTED) {
            throw new BusinessValidationException("Request can only be approved or rejected");
        }

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!request.getCompany().getId().equals(hrUser.getCompany().getId())) {
            throw new AccessDeniedException("You cannot handle requests for another company");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessValidationException(
                    "This request has already been " + request.getStatus().name().toLowerCase()
            );
        }

        /*
         * Important:
         * Apply schedule changes only when HR approves.
         * Rejecting a request should not modify schedules.
         */
        if (newStatus == RequestStatus.APPROVED) {
            applyApprovedRequestToSchedule(request);
        }

        request.setStatus(newStatus);
        request.setHandledBy(hrUser);

        Request saved = requestRepository.save(request);
        return toResponse(saved);
    }

    private void applyApprovedRequestToSchedule(Request request) {
        User requester = request.getRequester();

        if (requester == null || requester.getId() == null) {
            throw new BusinessValidationException("Request has no valid requester");
        }

        WorkMode targetWorkMode = mapRequestTypeToWorkMode(request.getType());

        List<ScheduleEntry> entriesToUpdate = new ArrayList<>();

        LocalDate date = request.getStartDate();

        while (!date.isAfter(request.getEndDate())) {
            LocalDate currentDate  = date;
            ScheduleEntry entry = scheduleEntryRepository
                    .findPublishedEntryForUserOnDate(requester.getId(), currentDate )
                    .orElseThrow(() -> new BusinessValidationException(
                            "Cannot approve request. User "
                                    + requester.getEmail()
                                    + " has no published schedule entry on "
                                    + currentDate 
                    ));

            entry.setWorkMode(targetWorkMode);
            entriesToUpdate.add(entry);

            date = date.plusDays(1);
        }

        scheduleEntryRepository.saveAll(entriesToUpdate);

        // Handle consequences for meetings and tasks
        if (request.getType() == RequestType.PTO) {
            meetingService.handlePtoRequest(requester, request.getStartDate(), request.getEndDate());
            taskService.handlePtoRequest(requester, request.getStartDate(), request.getEndDate());
        } else if (request.getType() == RequestType.WFH) {
            meetingService.handleWfhRequest(requester, request.getStartDate(), request.getEndDate());
        }
    }

    private WorkMode mapRequestTypeToWorkMode(RequestType requestType) {
        if (requestType == null) {
            throw new BusinessValidationException("Request type is required");
        }

        return switch (requestType) {
            case PTO -> WorkMode.OFF;
            case WFH -> WorkMode.ONLINE;
        };
    }

    private void validateSubmission(RequestSubmissionDTO dto) {
        if (dto == null) {
            throw new BusinessValidationException("Request body is required");
        }

        if (dto.getType() == null) {
            throw new BusinessValidationException("type is required");
        }

        if (dto.getStartDate() == null) {
            throw new BusinessValidationException("startDate is required");
        }

        if (dto.getEndDate() == null) {
            throw new BusinessValidationException("endDate is required");
        }

        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessValidationException("endDate must be on or after startDate");
        }
    }

    private boolean datesOverlap(
            LocalDate startA,
            LocalDate endA,
            LocalDate startB,
            LocalDate endB
    ) {
        return !endA.isBefore(startB) && !startA.isAfter(endB);
    }

    private void validateAuthenticatedUser(User user) {
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (user.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        if (user.getTeam() == null) {
            throw new AccessDeniedException("You are not attached to a team");
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