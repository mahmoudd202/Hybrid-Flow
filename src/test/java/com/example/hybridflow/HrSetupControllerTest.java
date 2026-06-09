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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class HrSetupControllerTest {

        @Autowired
        WebApplicationContext webApplicationContext;
        @Autowired
        OfficeRepository officeRepository;
        @Autowired
        TeamRepository teamRepository;

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

                String body = objectMapper.writeValueAsString(
                                Map.of("email", "hr@techflow.com", "password", "password123"));

                MvcResult result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andReturn();

                hrToken = objectMapper.readTree(result.getResponse().getContentAsString())
                                .get("accessToken").asText();

                seededOfficeId = officeRepository.findAll().get(0).getId();
        }

        @Test
        void getCompanyOfficesReturnsMainHq() throws Exception {
                mockMvc.perform(get("/api/offices/company")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].name").value("Main HQ - New York"));
        }

        @Test
        void getTeamsByOfficeReturnsBothTeams() throws Exception {
                mockMvc.perform(get("/api/teams/office/" + seededOfficeId)
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void getPoliciesReturnsSeededPolicy() throws Exception {
                mockMvc.perform(get("/api/planning-policies")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].name").value("Standard Hybrid Policy"));
        }

        @Test
        void planningPolicyCrudCycle() throws Exception {
                String newPolicyBody = objectMapper.writeValueAsString(Map.of(
                                "name", "Test Policy",
                                "workingDaysPerWeek", 5,
                                "minOfficeDaysPerWeek", 1,
                                "maxOfficeDaysPerWeek", 3,
                                "maxConsecutiveOfficeDays", 2,
                                "minTeamSharedDays", 1,
                                "coPresenceThresholdPercentagePerDay", 40));

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

                mockMvc.perform(get("/api/planning-policies")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
                String updateBody = objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Policy Name",
                                "workingDaysPerWeek", 5,
                                "minOfficeDaysPerWeek", 1,
                                "maxOfficeDaysPerWeek", 3,
                                "maxConsecutiveOfficeDays", 2,
                                "minTeamSharedDays", 1,
                                "coPresenceThresholdPercentagePerDay", 40));

                mockMvc.perform(put("/api/planning-policies/" + newPolicyId)
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Updated Policy Name"));

                mockMvc.perform(delete("/api/planning-policies/" + newPolicyId)
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isNoContent());
                mockMvc.perform(get("/api/planning-policies")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void officeCrudCycle() throws Exception {
                String newOfficeBody = objectMapper.writeValueAsString(Map.of(
                                "name", "London Office",
                                "maxCapacity", 50));

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

                String updateOfficeBody = objectMapper.writeValueAsString(Map.of(
                                "name", "London HQ",
                                "maxCapacity", 100));

                mockMvc.perform(put("/api/offices/" + newOfficeId)
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateOfficeBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("London HQ"))
                                .andExpect(jsonPath("$.maxCapacity").value(100));

                mockMvc.perform(delete("/api/offices/" + seededOfficeId)
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().is4xxClientError());
                mockMvc.perform(delete("/api/offices/" + newOfficeId)
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isNoContent());
        }

        @Test
        void teamCrudCycle() throws Exception {
                String newTeamBody = objectMapper.writeValueAsString(Map.of(
                                "name", "Quality Assurance",
                                "officeId", seededOfficeId));

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

                String updateTeamBody = objectMapper.writeValueAsString(Map.of(
                                "name", "QA & Testing",
                                "officeId", seededOfficeId));

                mockMvc.perform(put("/api/teams/" + newTeamId)
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateTeamBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("QA & Testing"));

                Long seededTeamId = teamRepository.findAll().get(0).getId();
                mockMvc.perform(delete("/api/teams/" + seededTeamId)
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().is4xxClientError());
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

                if (response.size() > 0) {
                        JsonNode firstMember = response.get(0);
                        assertThat(firstMember.has("id")).isTrue();
                        assertThat(firstMember.has("email")).isTrue();
                        assertThat(firstMember.has("role")).isTrue();
                }
        }
}
