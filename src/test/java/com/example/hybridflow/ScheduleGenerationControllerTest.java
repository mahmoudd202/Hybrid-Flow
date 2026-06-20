package com.example.hybridflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.PlanningPolicyRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.entity.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduleGenerationControllerTest {

        @Autowired
        WebApplicationContext webApplicationContext;
        @Autowired
        OfficeRepository officeRepository;
        @Autowired
        TeamRepository teamRepository;
        @Autowired
        PlanningPolicyRepository planningPolicyRepository;
        @Autowired
        UserRepository userRepository;

        MockMvc mockMvc;
        final ObjectMapper objectMapper = new ObjectMapper();

        String hrToken;
        Long seededOfficeId;
        List<Long> seededTeamIds;
        Long seededPolicyId;

        LocalDate publishRangeStart;
        LocalDate publishRangeEnd;

        LocalDate discardRangeStart;
        LocalDate discardRangeEnd;

        @BeforeEach
        void setUp() throws Exception {
                mockMvc = MockMvcBuilders
                                .webAppContextSetup(webApplicationContext)
                                .apply(SecurityMockMvcConfigurers.springSecurity())
                                .build();

                String loginBody = objectMapper.writeValueAsString(
                                Map.of("email", "hr@techflow.com", "password", "password123"));
                MvcResult loginResult = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andReturn();
                hrToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("accessToken").asText();

                seededOfficeId = officeRepository.findAll().get(0).getId();
                seededTeamIds = teamRepository.findAll().stream().map(t -> t.getId()).toList();
                seededPolicyId = planningPolicyRepository.findAll().get(0).getId();

                publishRangeStart = LocalDate.now().plusWeeks(6);
                while (publishRangeStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
                        publishRangeStart = publishRangeStart.plusDays(1);
                }
                publishRangeEnd = publishRangeStart.plusDays(4);

                discardRangeStart = LocalDate.now().plusWeeks(8);
                while (discardRangeStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
                        discardRangeStart = discardRangeStart.plusDays(1);
                }
                discardRangeEnd = discardRangeStart.plusDays(4);

                mockMvc.perform(delete("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andReturn();
        }

        @Test
        void getAvailableTeamsReturnsBothSeededTeams() throws Exception {
                LocalDate checkStart = LocalDate.now().plusWeeks(10);
                LocalDate checkEnd = checkStart.plusDays(4);

                MvcResult result = mockMvc.perform(get("/api/schedules/available-teams")
                                .header("Authorization", "Bearer " + hrToken)
                                .param("officeId", seededOfficeId.toString())
                                .param("startDate", checkStart.toString())
                                .param("endDate", checkEnd.toString()))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(body.get("availableTeams")).isNotNull();
                assertThat(body.get("availableTeams").size())
                                .as("Both seeded teams should be available for a date range with no existing schedules")
                                .isEqualTo(2);
                assertThat(body.get("unavailableTeams").size())
                                .as("No teams should be unavailable for this clean future date range")
                                .isEqualTo(0);
        }

        @Test
        void schedulableQueriesExcludeDeactivatedUsers() {
                User dev1 = userRepository.findByEmail("dev1@techflow.com")
                                .orElseThrow(() -> new AssertionError("Seeded dev1 user must exist"));
                Long teamId = dev1.getTeam().getId();
                int countBefore = userRepository.countSchedulableUsersByTeamId(teamId);

                boolean originalEnabled = dev1.isEnabled();
                boolean originalDeactivated = dev1.isDeactivated();
                try {
                        dev1.setEnabled(false);
                        dev1.setDeactivated(true);
                        userRepository.save(dev1);

                        List<User> schedulableUsers = userRepository.findSchedulableUsersByTeamIds(List.of(teamId));

                        assertThat(schedulableUsers)
                                        .extracting(User::getEmail)
                                        .doesNotContain("dev1@techflow.com");
                        assertThat(userRepository.countSchedulableUsersByTeamId(teamId))
                                        .as("Deactivated users must not count as schedulable")
                                        .isEqualTo(countBefore - 1);
                } finally {
                        dev1.setEnabled(originalEnabled);
                        dev1.setDeactivated(originalDeactivated);
                        userRepository.save(dev1);
                }
        }

        @Test
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void fullScheduleGenerationAndPublicationCycle() throws Exception {
                MvcResult genResult = mockMvc.perform(post("/api/schedules/generate")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(buildGenerateBody(publishRangeStart, publishRangeEnd)))
                                .andExpect(status().isAccepted())
                                .andReturn();

                JsonNode acceptedDTO = objectMapper.readTree(genResult.getResponse().getContentAsString());
                long runId = acceptedDTO.get("runId").asLong();
                assertThat(runId).as("runId must be a positive database ID").isPositive();
                assertThat(acceptedDTO.get("status").asText())
                                .as("Initial status must be PENDING — solver has not started yet")
                                .isEqualTo("PENDING");
                assertThat(acceptedDTO.get("planningPolicyId").asLong())
                                .as("Accepted DTO must echo back the requested policy ID")
                                .isEqualTo(seededPolicyId);

                JsonNode runDTO = pollUntilTerminal(runId);
                assertThat(runDTO.get("jobStatus").asText())
                                .as("Gurobi must find a feasible solution — errorMessage: "
                                                + runDTO.path("errorMessage").asText("(none)"))
                                .isEqualTo("COMPLETED");

                assertThat(runDTO.get("scheduleIds").size())
                                .as("One schedule ID must be created per team")
                                .isGreaterThanOrEqualTo(1);
                assertThat(runDTO.get("overallFairnessScore").asDouble())
                                .as("Overall fairness score must be non-negative")
                                .isGreaterThanOrEqualTo(0.0);

                MvcResult unpubResult = mockMvc.perform(get("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode unpub = objectMapper.readTree(unpubResult.getResponse().getContentAsString());
                int draftCount = unpub.get("count").asInt();
                assertThat(draftCount)
                                .as("At least one unpublished draft must exist after successful generation")
                                .isGreaterThanOrEqualTo(1);
                assertThat(unpub.get("schedules").size()).isEqualTo(draftCount);

                List<Long> draftIds = new ArrayList<>();
                unpub.get("schedules").forEach(s -> draftIds.add(s.get("scheduleId").asLong()));

                String publishBody = objectMapper.writeValueAsString(Map.of("scheduleIds", draftIds));
                mockMvc.perform(post("/api/schedules/publish")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(publishBody))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void fullScheduleGenerationAndDiscardCycle() throws Exception {
                long runId = triggerGeneration(discardRangeStart, discardRangeEnd);

                JsonNode runDTO = pollUntilTerminal(runId);
                assertThat(runDTO.get("jobStatus").asText())
                                .as("Gurobi must complete before the discard test can proceed — errorMessage: "
                                                + runDTO.path("errorMessage").asText("(none)"))
                                .isEqualTo("COMPLETED");

                MvcResult beforeDelete = mockMvc.perform(get("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andReturn();
                assertThat(objectMapper.readTree(beforeDelete.getResponse().getContentAsString())
                                .get("count").asInt())
                                .as("Draft must exist before discard")
                                .isGreaterThanOrEqualTo(1);

                mockMvc.perform(delete("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/schedules/unpublished")
                                .header("Authorization", "Bearer " + hrToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(0));
        }

        private String buildGenerateBody(LocalDate start, LocalDate end) throws Exception {
                return objectMapper.writeValueAsString(Map.of(
                                "officeId", seededOfficeId,
                                "planningPolicyId", seededPolicyId,
                                "teamIds", seededTeamIds,
                                "startDate", start.toString(),
                                "endDate", end.toString()));
        }

        private long triggerGeneration(LocalDate start, LocalDate end) throws Exception {
                MvcResult result = mockMvc.perform(post("/api/schedules/generate")
                                .header("Authorization", "Bearer " + hrToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(buildGenerateBody(start, end)))
                                .andExpect(status().isAccepted())
                                .andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsString())
                                .get("runId").asLong();
        }

        private JsonNode pollUntilTerminal(long runId) throws Exception {
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(120);
                while (System.currentTimeMillis() < deadline) {
                        MvcResult poll = mockMvc.perform(
                                        get("/api/schedules/optimization-runs/" + runId)
                                                        .header("Authorization", "Bearer " + hrToken))
                                        .andExpect(status().isOk())
                                        .andReturn();
                        JsonNode body = objectMapper.readTree(poll.getResponse().getContentAsString());
                        String status = body.get("jobStatus").asText();
                        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                                return body;
                        }
                        Thread.sleep(2_000);
                }
                throw new AssertionError(
                                "Optimization run " + runId + " did not reach a terminal state within 120 s");
        }
}
