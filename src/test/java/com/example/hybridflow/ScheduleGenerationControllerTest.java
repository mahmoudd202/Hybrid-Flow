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

import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.PlanningPolicyRepository;
import com.example.hybridflow.repository.TeamRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 3 — Schedule Generation & Publication
 *
 * Verifies HR can check available teams, generate a schedule via the async
 * Gurobi solver, poll for completion, publish the result, and discard drafts.
 *
 * Requires Gurobi 13.0.2 with a valid license installed on this machine.
 * Each full-cycle test has a 180-second wall-clock timeout to accommodate
 * the solver.
 *
 * Generation date ranges start 6–8 weeks in the future so they never overlap
 * the 4-week seeded schedule window.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ScheduleGenerationControllerTest {

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired OfficeRepository officeRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired PlanningPolicyRepository planningPolicyRepository;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    String hrToken;
    Long seededOfficeId;
    List<Long> seededTeamIds;
    Long seededPolicyId;

    /** Week-6 window: used by the publish-cycle test. */
    LocalDate publishRangeStart;
    LocalDate publishRangeEnd;

    /** Week-8 window: used by the discard-cycle test — no overlap with the above. */
    LocalDate discardRangeStart;
    LocalDate discardRangeEnd;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Login as HR and cache the token
        String loginBody = objectMapper.writeValueAsString(
                Map.of("email", "hr@techflow.com", "password", "password123"));
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn();
        hrToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Resolve seeded IDs directly from repositories (same pattern as other slices)
        seededOfficeId = officeRepository.findAll().get(0).getId();
        seededTeamIds  = teamRepository.findAll().stream().map(t -> t.getId()).toList();
        seededPolicyId = planningPolicyRepository.findAll().get(0).getId();

        // Future date ranges: both are outside the 4-week seeded schedule window
        publishRangeStart = LocalDate.now().plusWeeks(6);
        publishRangeEnd   = publishRangeStart.plusDays(4);
        discardRangeStart = LocalDate.now().plusWeeks(8);
        discardRangeEnd   = discardRangeStart.plusDays(4);

        // Clean up any leftover unpublished drafts so each test starts fresh
        mockMvc.perform(delete("/api/schedules/unpublished")
                        .header("Authorization", "Bearer " + hrToken))
                .andReturn();
    }

    // -----------------------------------------------------------------------
    // Step 1 — Available Teams
    // -----------------------------------------------------------------------

    @Test
    void getAvailableTeamsReturnsBothSeededTeams() throws Exception {
        // Use week-10 — a range that no other test in this class ever publishes into,
        // so this check is always clean regardless of test execution order.
        LocalDate checkStart = LocalDate.now().plusWeeks(10);
        LocalDate checkEnd   = checkStart.plusDays(4);

        MvcResult result = mockMvc.perform(get("/api/schedules/available-teams")
                        .header("Authorization", "Bearer " + hrToken)
                        .param("officeId",  seededOfficeId.toString())
                        .param("startDate", checkStart.toString())
                        .param("endDate",   checkEnd.toString()))
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

    // -----------------------------------------------------------------------
    // Steps 2–5 — Immediate 202 response, then async poll → publish
    // -----------------------------------------------------------------------

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void fullScheduleGenerationAndPublicationCycle() throws Exception {
        // STEP 2 — POST /api/schedules/generate must return HTTP 202 immediately
        // with status=PENDING and a valid runId (solver has not started yet).
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

        // STEP 3 — Poll GET /api/schedules/optimization-runs/{runId} until COMPLETED
        JsonNode runDTO = pollUntilTerminal(runId);
        assertThat(runDTO.get("jobStatus").asText())
                .as("Gurobi must find a feasible solution — errorMessage: "
                    + runDTO.path("errorMessage").asText("(none)"))
                .isEqualTo("COMPLETED");

        // Stats populated on COMPLETED
        assertThat(runDTO.get("scheduleIds").size())
                .as("One schedule ID must be created per team")
                .isGreaterThanOrEqualTo(1);
        assertThat(runDTO.get("overallFairnessScore").asDouble())
                .as("Overall fairness score must be non-negative")
                .isGreaterThanOrEqualTo(0.0);

        // STEP 5 — Draft must appear in GET /api/schedules/unpublished
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

        // Collect the schedule IDs returned in the unpublished list
        List<Long> draftIds = new ArrayList<>();
        unpub.get("schedules").forEach(s -> draftIds.add(s.get("scheduleId").asLong()));

        // STEP 6 — POST /api/schedules/publish — publish all drafts
        String publishBody = objectMapper.writeValueAsString(Map.of("scheduleIds", draftIds));
        mockMvc.perform(post("/api/schedules/publish")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isOk());

        // Published schedules must no longer appear in the unpublished list
        mockMvc.perform(get("/api/schedules/unpublished")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // -----------------------------------------------------------------------
    // Step 6 (discard path) — generate → poll → DELETE /api/schedules/unpublished
    // -----------------------------------------------------------------------

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void fullScheduleGenerationAndDiscardCycle() throws Exception {
        // Generate using the separate discard date range (no overlap with publish test)
        long runId = triggerGeneration(discardRangeStart, discardRangeEnd);

        // Wait for COMPLETED
        JsonNode runDTO = pollUntilTerminal(runId);
        assertThat(runDTO.get("jobStatus").asText())
                .as("Gurobi must complete before the discard test can proceed — errorMessage: "
                    + runDTO.path("errorMessage").asText("(none)"))
                .isEqualTo("COMPLETED");

        // Draft must be present
        MvcResult beforeDelete = mockMvc.perform(get("/api/schedules/unpublished")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(beforeDelete.getResponse().getContentAsString())
                .get("count").asInt())
                .as("Draft must exist before discard")
                .isGreaterThanOrEqualTo(1);

        // DELETE /api/schedules/unpublished — discard all drafts at once
        mockMvc.perform(delete("/api/schedules/unpublished")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk());

        // Unpublished list must now be empty
        mockMvc.perform(get("/api/schedules/unpublished")
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String buildGenerateBody(LocalDate start, LocalDate end) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "officeId",         seededOfficeId,
                "planningPolicyId", seededPolicyId,
                "teamIds",          seededTeamIds,
                "startDate",        start.toString(),
                "endDate",          end.toString()
        ));
    }

    /** Triggers POST /api/schedules/generate and returns the runId. */
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

    /**
     * Polls GET /api/schedules/optimization-runs/{runId} every 2 seconds until
     * jobStatus is COMPLETED or FAILED (both are terminal states).
     *
     * Throws AssertionError if the 120-second internal deadline expires first —
     * the test's own @Timeout provides an outer safety net.
     */
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
