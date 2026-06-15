package com.example.hybridflow.service;

import com.example.hybridflow.dto.CurrentUserResponseDTO;
import com.example.hybridflow.dto.EmployeeDetailsResponseDTO;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.UserProfile;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public EmployeeDetailsResponseDTO getEmployeeDetails(Long employeeId, User currentUser) {

        if (currentUser.getCompany() == null) {
            throw new BusinessValidationException("You are not assigned to a company.");
        }

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        if (employee.getCompany() == null ||
                !employee.getCompany().getId().equals(currentUser.getCompany().getId())) {
            throw new BusinessValidationException("You do not have access to this employee.");
        }
        UserProfile userProfile = userProfileRepository.findByUserId(employeeId)
                .orElse(null);

        return toEmployeeDetailsResponseDTO(employee, userProfile);
    }

    @Transactional
    public EmployeeDetailsResponseDTO updateEmployeeRole(Long employeeId, Role newRole, User currentUser) {
        if (currentUser.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR users can change employee roles.");
        }

        User employeeToUpdate = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        if (employeeToUpdate.getId().equals(currentUser.getId())) {
            throw new BusinessValidationException("You cannot change your own role.");
        }

        if (employeeToUpdate.getRole() == newRole) {
            throw new BusinessValidationException("Employee already has the role '" + newRole.name() + "'.");
        }

        if (!currentUser.getCompany().getId().equals(employeeToUpdate.getCompany().getId())) {
            throw new BusinessValidationException("You cannot change the role of an employee from another company.");
        }

        if (employeeToUpdate.getRole() == Role.MANAGER && newRole == Role.HR) {
            throw new BusinessValidationException("Cannot change role from MANAGER to HR directly. Please change the manager to an EMPLOYEE and assign another manager first, then change the old manager to HR.");
        }

        if (newRole == Role.MANAGER) {
            if (employeeToUpdate.getTeam() == null) {
                throw new BusinessValidationException(
                        "An employee must be assigned to a team before becoming a manager.");
            }
            User oldManager = employeeToUpdate.getTeam().getManager();
            if (oldManager != null && !oldManager.getId().equals(employeeToUpdate.getId())) {
                oldManager.setRole(Role.EMPLOYEE);
                userRepository.save(oldManager);
            }
            employeeToUpdate.getTeam().setManager(employeeToUpdate);
            teamRepository.save(employeeToUpdate.getTeam());
        } else if (employeeToUpdate.getRole() == Role.MANAGER && newRole != Role.MANAGER) {
            if (employeeToUpdate.getTeam() != null && employeeToUpdate.getTeam().getManager() != null &&
                    employeeToUpdate.getTeam().getManager().getId().equals(employeeToUpdate.getId())) {
                employeeToUpdate.getTeam().setManager(null);
                teamRepository.save(employeeToUpdate.getTeam());
            }
        }

        if (newRole == Role.HR) {
            employeeToUpdate.setTeam(null);
        }
        employeeToUpdate.setRole(newRole);
        User updatedUser = userRepository.save(employeeToUpdate);

        UserProfile userProfile = userProfileRepository.findByUserId(employeeId).orElse(null);
        return toEmployeeDetailsResponseDTO(updatedUser, userProfile);
    }

    @Transactional
    public EmployeeDetailsResponseDTO moveEmployeeToTeam(Long employeeId, Long newTeamId, User currentUser) {
        if (currentUser.getCompany() == null) {
            throw new BusinessValidationException("You are not assigned to a company.");
        }

        if (currentUser.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR users can move employees between teams.");
        }

        User employeeToMove = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        Team newTeam = teamRepository.findById(newTeamId)
                .orElseThrow(() -> new ResourceNotFoundException("New team not found."));

        if (employeeToMove.getTeam() != null && employeeToMove.getTeam().getId().equals(newTeamId)) {
            throw new BusinessValidationException(
                    "Employee is already a member of team '" + newTeam.getName() + "'.");
        }

        if (employeeToMove.getCompany() == null ||
                !currentUser.getCompany().getId().equals(employeeToMove.getCompany().getId())) {
            throw new BusinessValidationException("You cannot move an employee from another company.");
        }

        if (newTeam.getCompany() == null ||
                !currentUser.getCompany().getId().equals(newTeam.getCompany().getId())) {
            throw new BusinessValidationException("You cannot move an employee to a team in another company.");
        }

        if (employeeToMove.getRole() == Role.MANAGER && employeeToMove.getTeam() != null &&
                employeeToMove.getTeam().getManager() != null &&
                employeeToMove.getTeam().getManager().getId().equals(employeeToMove.getId())) {
            employeeToMove.getTeam().setManager(null);
            teamRepository.save(employeeToMove.getTeam());

            employeeToMove.setRole(Role.EMPLOYEE);
        }

        employeeToMove.setTeam(newTeam);
        User updatedUser = userRepository.save(employeeToMove);

        UserProfile userProfile = userProfileRepository.findByUserId(employeeId).orElse(null);
        return toEmployeeDetailsResponseDTO(updatedUser, userProfile);
    }

    @Transactional
    public EmployeeDetailsResponseDTO deactivateEmployee(Long employeeId, User currentUser) {

        if (currentUser.getCompany() == null) {
            throw new BusinessValidationException("You are not assigned to a company.");
        }

        User employeeToDeactivate = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));

        if (employeeToDeactivate.getCompany() == null ||
                !currentUser.getCompany().getId().equals(employeeToDeactivate.getCompany().getId())) {
            throw new BusinessValidationException("You cannot deactivate an employee from another company.");
        }

        if (currentUser.getId().equals(employeeToDeactivate.getId())) {
            throw new BusinessValidationException("You cannot deactivate your own account.");
        }

        if (employeeToDeactivate.getRole() == Role.HR) {
            throw new BusinessValidationException("HR accounts cannot be deactivated through this endpoint.");
        }

        if (!employeeToDeactivate.isEnabled()) {
            throw new BusinessValidationException("Employee account is already deactivated.");
        }

        if (employeeToDeactivate.getRole() == Role.MANAGER &&
                employeeToDeactivate.getTeam() != null &&
                employeeToDeactivate.getTeam().getManager() != null &&
                employeeToDeactivate.getTeam().getManager().getId().equals(employeeToDeactivate.getId())) {

            Team team = employeeToDeactivate.getTeam();

            team.setManager(null);
            teamRepository.save(team);
        }

        employeeToDeactivate.setEnabled(false);
        User updatedUser = userRepository.save(employeeToDeactivate);

        UserProfile userProfile = userProfileRepository.findByUserId(employeeId).orElse(null);

        return toEmployeeDetailsResponseDTO(updatedUser, userProfile);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponseDTO getMe(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);

        return CurrentUserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getCompanyName() : null)
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .teamName(user.getTeam() != null ? user.getTeam().getName() : null)
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EmployeeDetailsResponseDTO> getAllEmployees(User currentUser) {
        if (currentUser.getCompany() == null) {
            throw new BusinessValidationException("You are not assigned to a company.");
        }

        List<User> employees = userRepository.findAllByCompanyIdWithProfileAndTeam(currentUser.getCompany().getId());

        return employees.stream()
                .map(employee -> toEmployeeDetailsResponseDTO(employee, employee.getProfile()))
                .collect(Collectors.toList());
    }

    private EmployeeDetailsResponseDTO toEmployeeDetailsResponseDTO(User employee, UserProfile userProfile) {
        EmployeeDetailsResponseDTO.EmployeeDetailsResponseDTOBuilder builder = EmployeeDetailsResponseDTO.builder()
                .id(employee.getId())
                .email(employee.getEmail())
                .enabled(employee.isEnabled())
                .role(employee.getRole());

        if (userProfile != null) {
            builder.firstName(userProfile.getFirstName())
                    .lastName(userProfile.getLastName())
                    .dateOfBirth(userProfile.getDateOfBirth())
                    .nationality(userProfile.getNationality());
        }

        if (employee.getTeam() != null) {
            builder.team(EmployeeDetailsResponseDTO.TeamInfoDTO.builder()
                    .id(employee.getTeam().getId())
                    .name(employee.getTeam().getName())
                    .build());
        }

        if (employee.getTeam() != null && employee.getTeam().getOffice() != null) {
            builder.office(EmployeeDetailsResponseDTO.OfficeInfoDTO.builder()
                    .id(employee.getTeam().getOffice().getId())
                    .name(employee.getTeam().getOffice().getName())
                    .build());
        }

        return builder.build();
    }
}
