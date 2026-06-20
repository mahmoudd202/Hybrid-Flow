package com.example.hybridflow.dto;

import com.example.hybridflow.entity.Role;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class EmployeeDetailsResponseDTO {

    private Long      id;
    private String    email;
    private Role      role;
    private boolean   enabled;
    private boolean   deactivated;

    private String    firstName;
    private String    lastName;
    private LocalDate dateOfBirth;
    private String    nationality;

    private TeamInfoDTO   team;
    private OfficeInfoDTO office;

    @Data
    @Builder
    public static class TeamInfoDTO {
        private Long   id;
        private String name;
    }

    @Data
    @Builder
    public static class OfficeInfoDTO {
        private Long   id;
        private String name;
    }
}
