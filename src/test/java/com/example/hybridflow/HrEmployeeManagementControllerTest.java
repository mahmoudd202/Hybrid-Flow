package com.example.hybridflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.InvitationRepository;
import com.example.hybridflow.repository.CompanyRepository;
import com.example.hybridflow.entity.Invitation;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.Role;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 6 — HR Employee Management & Request Approval
 *
 * Verifies HR can approve/reject requests, view employee schedules,
 * and manage employee roles, teams, and activation status.
 *
 * @Transactional rolls back database changes after each test so every test
 * starts with the seeded request in PENDING state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class HrEmployeeManagementControllerTest {

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired InvitationRepository invitationRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String hrToken;
    Long dev1Id;
    Long seededRequestId;
    Long uiUxTeamId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Login as HR
        String body = objectMapper.writeValueAsString(
                Map.of("email", "hr@techflow.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        hrToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        dev1Id = userRepository.findByEmail("dev1@techflow.com").orElseThrow().getId();

        seededRequestId = requestRepository.findByRequesterId(dev1Id).get(0).getId();

        uiUxTeamId = teamRepository.findAll().stream()
                .filter(t -> "UI/UX Design".equals(t.getName()))
                .findFirst().orElseThrow().getId();
    }

    // -----------------------------------------------------------------------
    // Requests — pending list
    // -----------------------------------------------------------------------

    @Test
    void getPendingRequestsReturnsDev1WfhRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/requests/pending")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode requests = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(requests.isArray()).isTrue();
        assertThat(requests.size()).isGreaterThanOrEqualTo(1);

        boolean foundDev1 = false;
        for (JsonNode req : requests) {
            if (req.get("requesterEmail").asText().equals("dev1@techflow.com")
                    && req.get("status").asText().equals("PENDING")) {
                foundDev1 = true;
                break;
            }
        }
        assertThat(foundDev1).as("dev1's PENDING WFH request must appear in the pending list").isTrue();
    }

    // -----------------------------------------------------------------------
    // Requests — approve
    // -----------------------------------------------------------------------

    @Test
    void approveRequestChangesStatusToApproved() throws Exception {
        mockMvc.perform(patch("/api/requests/" + seededRequestId + "/approve")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // -----------------------------------------------------------------------
    // Requests — reject (reject the seeded request directly; @Transactional
    // ensures it is PENDING at the start of this test)
    // -----------------------------------------------------------------------

    @Test
    void rejectRequestChangesStatusToRejected() throws Exception {
        mockMvc.perform(patch("/api/requests/" + seededRequestId + "/reject")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // -----------------------------------------------------------------------
    // Schedule — HR views dev1's schedule
    // -----------------------------------------------------------------------

    @Test
    void getEmployeeScheduleReturnsDev1Entries() throws Exception {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now().plusDays(21);

        MvcResult result = mockMvc.perform(get("/api/schedules/employees/" + dev1Id)
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("teams")).isTrue();
        assertThat(body.get("teams").size()).isGreaterThan(0);
        JsonNode members = body.get("teams").get(0).get("members");
        assertThat(members).isNotNull();
        assertThat(members.size()).isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // User management — role change
    // Promoting to MANAGER is blocked when the team already has one, so we
    // test EMPLOYEE → HR which has no team-manager constraint.
    // -----------------------------------------------------------------------

    @Test
    void updateRoleChangesDevToHr() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("newRole", "HR"));

        MvcResult result = mockMvc.perform(patch("/users/" + dev1Id + "/role")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("role").asText()).isEqualTo("HR");
        // @Transactional rolls back — no manual restore needed
    }

    // -----------------------------------------------------------------------
    // User management — team move
    // -----------------------------------------------------------------------

    @Test
    void moveEmployeeToUiUxTeam() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("newTeamId", uiUxTeamId));

        MvcResult result = mockMvc.perform(patch("/users/" + dev1Id + "/team")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("team").get("name").asText()).isEqualTo("UI/UX Design");
        // @Transactional rolls back — no manual restore needed
    }

    // -----------------------------------------------------------------------
    // User management — deactivate, then login fails
    // -----------------------------------------------------------------------

    @Test
    void deactivateUserBlocksLogin() throws Exception {
        mockMvc.perform(patch("/users/" + dev1Id + "/deactivate")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Spring Security rejects disabled accounts — login must fail
        String loginBody = objectMapper.writeValueAsString(
                Map.of("email", "dev1@techflow.com", "password", "password123"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().is4xxClientError());
    }

    // -----------------------------------------------------------------------
    // User management — list all employees
    // -----------------------------------------------------------------------

    @Test
    void getAllEmployeesAsHrReturnsEmployeeList() throws Exception {
        MvcResult result = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.isArray()).isTrue();
        assertThat(response.size()).isGreaterThanOrEqualTo(2);

        // Verify some known fields and content
        boolean foundDev1 = false;
        boolean foundHr = false;
        for (JsonNode emp : response) {
            String email = emp.get("email").asText();
            if ("dev1@techflow.com".equals(email)) {
                foundDev1 = true;
                assertThat(emp.has("firstName")).isTrue();
                assertThat(emp.has("lastName")).isTrue();
                assertThat(emp.has("role")).isTrue();
            } else if ("hr@techflow.com".equals(email)) {
                foundHr = true;
            }
        }
        assertThat(foundDev1).isTrue();
        assertThat(foundHr).isTrue();
    }

    @Test
    void getAllEmployeesAsEmployeeReturnsSuccess() throws Exception {
        String employeeBody = objectMapper.writeValueAsString(
                Map.of("email", "dev1@techflow.com", "password", "password123"));

        MvcResult employeeResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeBody))
                .andExpect(status().isOk())
                .andReturn();

        String employeeToken = objectMapper.readTree(employeeResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Request history integration tests
    // -----------------------------------------------------------------------

    @Test
    void getRequestHistoryAsHrReturnsFullHistory() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/requests/history")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.isArray()).isTrue();
        assertThat(response.size()).isGreaterThanOrEqualTo(1);

        // Verify structure
        JsonNode firstItem = response.get(0);
        assertThat(firstItem.has("id")).isTrue();
        assertThat(firstItem.has("status")).isTrue();
        assertThat(firstItem.has("requesterEmail")).isTrue();
    }

    @Test
    void getRequestHistoryWithFilters() throws Exception {
        // Filter by Status: PENDING
        MvcResult resultPending = mockMvc.perform(get("/api/requests/history")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pendingResponse = objectMapper.readTree(resultPending.getResponse().getContentAsString());
        for (JsonNode req : pendingResponse) {
            assertThat(req.get("status").asText()).isEqualTo("PENDING");
        }

        // Filter by Requester ID
        MvcResult resultRequester = mockMvc.perform(get("/api/requests/history")
                        .param("requesterId", dev1Id.toString())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode requesterResponse = objectMapper.readTree(resultRequester.getResponse().getContentAsString());
        for (JsonNode req : requesterResponse) {
            assertThat(req.get("requesterId").asLong()).isEqualTo(dev1Id);
        }
    }

    @Test
    void getRequestHistoryAsEmployeeReturnsForbidden() throws Exception {
        String employeeBody = objectMapper.writeValueAsString(
                Map.of("email", "dev1@techflow.com", "password", "password123"));

        MvcResult employeeResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeBody))
                .andExpect(status().isOk())
                .andReturn();

        String employeeToken = objectMapper.readTree(employeeResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/requests/history")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Invitation Enhancement Tests
    // -----------------------------------------------------------------------

    @Test
    void resendInvitationSuccess() throws Exception {
        Company company = companyRepository.findByCompanyName("TechFlow Corp").orElseThrow();
        Invitation invite = new Invitation();
        invite.setEmail("new-invite@techflow.com");
        invite.setRole(Role.EMPLOYEE);
        invite.setCompany(company);
        invite.setExpiryDate(java.time.Instant.now().plusSeconds(10));
        invite.setUsed(false);
        invite = invitationRepository.save(invite);

        mockMvc.perform(post("/api/invitations/" + invite.getId() + "/resend")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitation resent successfully."))
                .andExpect(jsonPath("$.email").value("new-invite@techflow.com"));
        
        Invitation updated = invitationRepository.findById(invite.getId()).orElseThrow();
        assertThat(updated.getExpiryDate()).isAfter(java.time.Instant.now().plusSeconds(86300));
    }

    @Test
    void resendUsedInvitationThrowsException() throws Exception {
        Company company = companyRepository.findByCompanyName("TechFlow Corp").orElseThrow();
        Invitation invite = new Invitation();
        invite.setEmail("used-invite@techflow.com");
        invite.setRole(Role.EMPLOYEE);
        invite.setCompany(company);
        invite.setExpiryDate(java.time.Instant.now().plusSeconds(86400));
        invite.setUsed(true);
        invite = invitationRepository.save(invite);

        mockMvc.perform(post("/api/invitations/" + invite.getId() + "/resend")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cancelInvitationSuccess() throws Exception {
        Company company = companyRepository.findByCompanyName("TechFlow Corp").orElseThrow();
        Invitation invite = new Invitation();
        invite.setEmail("to-cancel@techflow.com");
        invite.setRole(Role.EMPLOYEE);
        invite.setCompany(company);
        invite.setExpiryDate(java.time.Instant.now().plusSeconds(86400));
        invite.setUsed(false);
        invite = invitationRepository.save(invite);

        // Test DELETE endpoint
        mockMvc.perform(delete("/api/invitations/" + invite.getId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());

        assertThat(invitationRepository.findById(invite.getId())).isEmpty();
    }

    @Test
    void expireInvitationSuccess() throws Exception {
        Company company = companyRepository.findByCompanyName("TechFlow Corp").orElseThrow();
        Invitation invite = new Invitation();
        invite.setEmail("to-expire@techflow.com");
        invite.setRole(Role.EMPLOYEE);
        invite.setCompany(company);
        invite.setExpiryDate(java.time.Instant.now().plusSeconds(86400));
        invite.setUsed(false);
        invite = invitationRepository.save(invite);

        mockMvc.perform(post("/api/invitations/" + invite.getId() + "/expire")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitation expired successfully."));

        Invitation updated = invitationRepository.findById(invite.getId()).orElseThrow();
        assertThat(updated.getExpiryDate()).isBefore(java.time.Instant.now());
    }

    @Test
    void tenantIsolationEnforcedOnInvitationManagement() throws Exception {
        // Create another company
        Company otherCompany = new Company();
        otherCompany.setCompanyName("Other Corp");
        otherCompany = companyRepository.save(otherCompany);

        Invitation otherInvite = new Invitation();
        otherInvite.setEmail("other-invite@othercorp.com");
        otherInvite.setRole(Role.EMPLOYEE);
        otherInvite.setCompany(otherCompany);
        otherInvite.setExpiryDate(java.time.Instant.now().plusSeconds(86400));
        otherInvite.setUsed(false);
        otherInvite = invitationRepository.save(otherInvite);

        // Try resending, cancelling, and expiring with TechFlow HR token
        mockMvc.perform(post("/api/invitations/" + otherInvite.getId() + "/resend")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/invitations/" + otherInvite.getId())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/invitations/" + otherInvite.getId() + "/expire")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());
    }

    // -----------------------------------------------------------------------
    // Team management — list company teams
    // -----------------------------------------------------------------------

    @Test
    void getTeamsByCompanyReturnsAllTeamsOfCompany() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/teams/company")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.isArray()).isTrue();
        assertThat(response.size()).isGreaterThanOrEqualTo(2);

        // Verify structure
        JsonNode firstTeam = response.get(0);
        assertThat(firstTeam.has("id")).isTrue();
        assertThat(firstTeam.has("name")).isTrue();
        assertThat(firstTeam.has("companyId")).isTrue();
    }
}
