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
import com.example.hybridflow.entity.Team;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class HrEmployeeManagementControllerTest {

        @Autowired
        WebApplicationContext webApplicationContext;
        @Autowired
        RequestRepository requestRepository;
        @Autowired
        UserRepository userRepository;
        @Autowired
        TeamRepository teamRepository;
        @Autowired
        InvitationRepository invitationRepository;
        @Autowired
        CompanyRepository companyRepository;
        @Autowired
        PasswordEncoder passwordEncoder;

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

        @Test
        void approveRequestChangesStatusToApproved() throws Exception {
                mockMvc.perform(patch("/api/requests/" + seededRequestId + "/approve")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        void rejectRequestChangesStatusToRejected() throws Exception {
                mockMvc.perform(patch("/api/requests/" + seededRequestId + "/reject")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("REJECTED"));
        }

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

                JsonNode entries = members.get(0).get("entries");
                assertThat(entries).isNotNull();
                assertThat(entries.size()).isGreaterThan(0);
                JsonNode firstEntry = entries.get(0);
                assertThat(firstEntry.has("officeName")).isTrue();
                assertThat(firstEntry.get("officeName").asText()).isEqualTo("Main HQ - New York");
        }

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
        }

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
        }

        @Test
        void deactivateUserBlocksLogin() throws Exception {
                mockMvc.perform(patch("/users/" + dev1Id + "/deactivate")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.enabled").value(false));

                String loginBody = objectMapper.writeValueAsString(
                                Map.of("email", "dev1@techflow.com", "password", "password123"));

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().is4xxClientError());
        }

        @Test
        void getAllEmployeesAsHrReturnsEmployeeList() throws Exception {
                MvcResult result = mockMvc.perform(get("/users")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(response.isArray()).isTrue();
                assertThat(response.size()).isGreaterThanOrEqualTo(2);

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

        @Test
        void getRequestHistoryAsHrReturnsFullHistory() throws Exception {
                MvcResult result = mockMvc.perform(get("/api/requests/history")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(response.isArray()).isTrue();
                assertThat(response.size()).isGreaterThanOrEqualTo(1);

                JsonNode firstItem = response.get(0);
                assertThat(firstItem.has("id")).isTrue();
                assertThat(firstItem.has("status")).isTrue();
                assertThat(firstItem.has("requesterEmail")).isTrue();
        }

        @Test
        void getRequestHistoryWithFilters() throws Exception {
                MvcResult resultPending = mockMvc.perform(get("/api/requests/history")
                                .param("status", "PENDING")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode pendingResponse = objectMapper.readTree(resultPending.getResponse().getContentAsString());
                for (JsonNode req : pendingResponse) {
                        assertThat(req.get("status").asText()).isEqualTo("PENDING");
                }

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

        @Test
        void getTeamsByCompanyReturnsAllTeamsOfCompany() throws Exception {
                MvcResult result = mockMvc.perform(get("/api/teams/company")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(response.isArray()).isTrue();
                assertThat(response.size()).isGreaterThanOrEqualTo(2);

                JsonNode firstTeam = response.get(0);
                assertThat(firstTeam.has("id")).isTrue();
                assertThat(firstTeam.has("name")).isTrue();
                assertThat(firstTeam.has("companyId")).isTrue();
        }

        @Test
        void sendManagerInvitationDemotesOldManager() throws Exception {
                Team team = teamRepository.findAll().stream()
                                .filter(t -> "Backend Devs".equals(t.getName()))
                                .findFirst().orElseThrow();

                User oldManager = team.getManager();
                assertThat(oldManager).isNotNull();
                assertThat(oldManager.getRole()).isEqualTo(Role.MANAGER);

                Map<String, Object> invitePayload = Map.of(
                                "email", "new-manager@techflow.com",
                                "role", "MANAGER",
                                "teamId", team.getId());

                mockMvc.perform(post("/api/invitations/send")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invitePayload)))
                                .andExpect(status().isOk());

                User oldManagerUpdated = userRepository.findById(oldManager.getId()).orElseThrow();
                assertThat(oldManagerUpdated.getRole()).isEqualTo(Role.EMPLOYEE);

                Team teamUpdated = teamRepository.findById(team.getId()).orElseThrow();
                assertThat(teamUpdated.getManager()).isNull();

                Invitation invitation = invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(
                                "new-manager@techflow.com", java.time.Instant.now()).orElseThrow();
                assertThat(invitation.getRole()).isEqualTo(Role.MANAGER);
                Map<String, Object> registerPayload = Map.of(
                                "email", "new-manager@techflow.com",
                                "password", "password123",
                                "firstName", "John",
                                "lastName", "Doe",
                                "dateOfBirth", "1990-01-01",
                                "nationality", "American");

                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerPayload)))
                                .andExpect(status().isCreated());

                Team teamWithNewManager = teamRepository.findById(team.getId()).orElseThrow();
                assertThat(teamWithNewManager.getManager()).isNotNull();
                assertThat(teamWithNewManager.getManager().getEmail()).isEqualTo("new-manager@techflow.com");
        }

        @Test
        void sendManagerInvitationDemotesPendingManagerInvitations() throws Exception {
                Team team = teamRepository.findAll().stream()
                                .filter(t -> "Backend Devs".equals(t.getName()))
                                .findFirst().orElseThrow();

                Map<String, Object> invitePayload1 = Map.of(
                                "email", "manager1@techflow.com",
                                "role", "MANAGER",
                                "teamId", team.getId());
                mockMvc.perform(post("/api/invitations/send")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invitePayload1)))
                                .andExpect(status().isOk());

                Map<String, Object> invitePayload2 = Map.of(
                                "email", "manager2@techflow.com",
                                "role", "MANAGER",
                                "teamId", team.getId());
                mockMvc.perform(post("/api/invitations/send")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invitePayload2)))
                                .andExpect(status().isOk());

                Invitation invite1 = invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(
                                "manager1@techflow.com", java.time.Instant.now()).orElseThrow();
                assertThat(invite1.getRole()).isEqualTo(Role.EMPLOYEE);
                Invitation invite2 = invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(
                                "manager2@techflow.com", java.time.Instant.now()).orElseThrow();
                assertThat(invite2.getRole()).isEqualTo(Role.MANAGER);

                Map<String, Object> registerPayload1 = Map.of(
                                "email", "manager1@techflow.com",
                                "password", "password123",
                                "firstName", "Manager",
                                "lastName", "One",
                                "dateOfBirth", "1990-01-01",
                                "nationality", "American");
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerPayload1)))
                                .andExpect(status().isCreated());

                Map<String, Object> registerPayload2 = Map.of(
                                "email", "manager2@techflow.com",
                                "password", "password123",
                                "firstName", "Manager",
                                "lastName", "Two",
                                "dateOfBirth", "1990-01-01",
                                "nationality", "American");
                mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerPayload2)))
                                .andExpect(status().isCreated());

                User registered1 = userRepository.findByEmail("manager1@techflow.com").orElseThrow();
                assertThat(registered1.getRole()).isEqualTo(Role.EMPLOYEE);

                User registered2 = userRepository.findByEmail("manager2@techflow.com").orElseThrow();
                assertThat(registered2.getRole()).isEqualTo(Role.MANAGER);

                Team teamUpdated = teamRepository.findById(team.getId()).orElseThrow();
                assertThat(teamUpdated.getManager()).isNotNull();
                assertThat(teamUpdated.getManager().getEmail()).isEqualTo("manager2@techflow.com");
        }
}
