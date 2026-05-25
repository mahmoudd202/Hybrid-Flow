package com.example.hybridflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 2 — HR Setup
 *
 * Verifies HR can read offices and teams, and perform full CRUD on planning policies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class HrSetupControllerTest {

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired OfficeRepository officeRepository;
    @Autowired TeamRepository teamRepository;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    String hrToken;
    Long seededOfficeId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Log in as HR and cache the token for all tests in this class
        String body = objectMapper.writeValueAsString(
                Map.of("email", "hr@techflow.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        hrToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Resolve the seeded office ID directly from the repository
        seededOfficeId = officeRepository.findAll().get(0).getId();
    }

    // -----------------------------------------------------------------------
    // Offices
    // -----------------------------------------------------------------------

    @Test
    void getCompanyOfficesReturnsMainHq() throws Exception {
        mockMvc.perform(get("/api/offices/company")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Main HQ - New York"));
    }

    // -----------------------------------------------------------------------
    // Teams
    // -----------------------------------------------------------------------

    @Test
    void getTeamsByOfficeReturnsBothTeams() throws Exception {
        mockMvc.perform(get("/api/teams/office/" + seededOfficeId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // -----------------------------------------------------------------------
    // Planning policies — read
    // -----------------------------------------------------------------------

    @Test
    void getPoliciesReturnsSeededPolicy() throws Exception {
        mockMvc.perform(get("/api/planning-policies")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Standard Hybrid Policy"));
    }

    // -----------------------------------------------------------------------
    // Planning policies — full CRUD cycle in one test to keep ordering simple
    // -----------------------------------------------------------------------

    @Test
    void planningPolicyCrudCycle() throws Exception {
        String newPolicyBody = objectMapper.writeValueAsString(Map.of(
                "name", "Test Policy",
                "workingDaysPerWeek", 5,
                "minOfficeDaysPerWeek", 1,
                "maxOfficeDaysPerWeek", 3,
                "maxConsecutiveOfficeDays", 2,
                "minTeamSharedDays", 1,
                "coPresenceThresholdPercentagePerDay", 40
        ));

        // CREATE — expect 201 with the new policy in the response body
        MvcResult createResult = mockMvc.perform(post("/api/planning-policies")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newPolicyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Policy"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        Long newPolicyId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // LIST — now contains 2 policies (seeded + new)
        mockMvc.perform(get("/api/planning-policies")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // UPDATE — rename the new policy
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Updated Policy Name",
                "workingDaysPerWeek", 5,
                "minOfficeDaysPerWeek", 1,
                "maxOfficeDaysPerWeek", 3,
                "maxConsecutiveOfficeDays", 2,
                "minTeamSharedDays", 1,
                "coPresenceThresholdPercentagePerDay", 40
        ));

        mockMvc.perform(put("/api/planning-policies/" + newPolicyId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Policy Name"));

        // DELETE
        mockMvc.perform(delete("/api/planning-policies/" + newPolicyId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());

        // LIST again — back to 1 (only the seeded policy remains)
        mockMvc.perform(get("/api/planning-policies")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void officeCrudCycle() throws Exception {
        String newOfficeBody = objectMapper.writeValueAsString(Map.of(
                "name", "London Office",
                "maxCapacity", 50
        ));

        // Create Office
        MvcResult createResult = mockMvc.perform(post("/api/offices")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newOfficeBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("London Office"))
                .andExpect(jsonPath("$.maxCapacity").value(50))
                .andReturn();

        Long newOfficeId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Update Office
        String updateOfficeBody = objectMapper.writeValueAsString(Map.of(
                "name", "London HQ",
                "maxCapacity", 100
        ));

        mockMvc.perform(put("/api/offices/" + newOfficeId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateOfficeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("London HQ"))
                .andExpect(jsonPath("$.maxCapacity").value(100));

        // Try deleting seeded office (has teams) -> should fail
        mockMvc.perform(delete("/api/offices/" + seededOfficeId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());

        // Delete newly created office -> should succeed
        mockMvc.perform(delete("/api/offices/" + newOfficeId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void teamCrudCycle() throws Exception {
        String newTeamBody = objectMapper.writeValueAsString(Map.of(
                "name", "Quality Assurance",
                "officeId", seededOfficeId
        ));

        // Create Team
        MvcResult createResult = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newTeamBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Quality Assurance"))
                .andExpect(jsonPath("$.officeId").value(seededOfficeId))
                .andReturn();

        Long newTeamId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Update Team
        String updateTeamBody = objectMapper.writeValueAsString(Map.of(
                "name", "QA & Testing",
                "officeId", seededOfficeId
        ));

        mockMvc.perform(put("/api/teams/" + newTeamId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTeamBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("QA & Testing"));

        // Try deleting seeded team (has employees) -> should fail
        Long seededTeamId = teamRepository.findAll().get(0).getId();
        mockMvc.perform(delete("/api/teams/" + seededTeamId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().is4xxClientError());

        // Delete newly created team -> should succeed
        mockMvc.perform(delete("/api/teams/" + newTeamId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void getTeamMembersReturnsMemberList() throws Exception {
        Long seededTeamId = teamRepository.findAll().get(0).getId();

        MvcResult result = mockMvc.perform(get("/api/teams/" + seededTeamId + "/members")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.isArray()).isTrue();
        
        // Assert some user fields are present
        if (response.size() > 0) {
            JsonNode firstMember = response.get(0);
            assertThat(firstMember.has("id")).isTrue();
            assertThat(firstMember.has("email")).isTrue();
            assertThat(firstMember.has("role")).isTrue();
        }
    }
}
