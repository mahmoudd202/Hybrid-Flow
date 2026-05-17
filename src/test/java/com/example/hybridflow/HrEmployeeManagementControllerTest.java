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
}
