package com.example.hybridflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 4 — Employee Experience
 *
 * Verifies that dev1 can view their schedule, meetings, tasks, preferences,
 * and manage WFH/PTO requests end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
@ActiveProfiles("test")
// @WithMockUser(username = "dev1", roles = {"EMPLOYEE"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class EmployeeExperienceControllerTest {

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired TaskAssignmentRepository taskAssignmentRepository;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired ScheduleEntryRepository scheduleEntryRepository;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String employeeToken;
    Long dev1Id;
    Long seededAssignmentId;
    Long seededRequestId;
    // A future date where dev1 has an OFFICE entry — guaranteed valid for a WFH request
    LocalDate wfhDate;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Login as dev1
        String body = objectMapper.writeValueAsString(
                Map.of("email", "dev1@techflow.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        employeeToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Resolve dev1's database ID from the repository
        dev1Id = userRepository.findByEmail("dev1@techflow.com").orElseThrow().getId();

        // Resolve seeded assignment for dev1 directly from repository
        seededAssignmentId = taskAssignmentRepository.findAllForAssignee(dev1Id)
                .get(0).getId();

        // Resolve seeded PENDING request for dev1
        seededRequestId = requestRepository.findByRequesterId(dev1Id)
                .get(0).getId();

        LocalDate seededRequestDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(1).plusDays(3);
        wfhDate = scheduleEntryRepository
                .findPublishedEntriesForUser(dev1Id, LocalDate.now().plusDays(1), LocalDate.now().plusDays(28))
                .stream()
                .filter(e -> e.getWorkMode() == WorkMode.OFFICE)
                .filter(e -> !e.getDate().equals(seededRequestDate)) // skip seeded request date
                .map(e -> e.getDate())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No future OFFICE entry found for dev1 — seed data issue"));
    }

    // -----------------------------------------------------------------------
    // Schedule
    // -----------------------------------------------------------------------

    @Test
    void getMyScheduleReturnsEntriesForDev1() throws Exception {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now().plusDays(21);

        MvcResult result = mockMvc.perform(get("/api/schedules/me")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // The response has a "teams" list; dev1's team must have at least one member row
        assertThat(body.has("teams")).isTrue();
        assertThat(body.get("teams").size()).isGreaterThan(0);
        JsonNode members = body.get("teams").get(0).get("members");
        assertThat(members).isNotNull();
        assertThat(members.size()).isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // Meetings
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "dev1", roles = {"EMPLOYEE"})
    void getMyMeetingsReturnsSprintPlanning() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/meetings/my-schedule")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode meetings = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(meetings.isArray()).isTrue();
        assertThat(meetings.size()).isGreaterThanOrEqualTo(1);

        boolean foundSprintPlanning = false;
        for (JsonNode meeting : meetings) {
            if ("Sprint Planning".equals(meeting.get("title").asText())) {
                foundSprintPlanning = true;
                break;
            }
        }
        assertThat(foundSprintPlanning).as("Sprint Planning meeting must be present").isTrue();
    }

    // -----------------------------------------------------------------------
    // Tasks — view and status update
    // -----------------------------------------------------------------------

    @Test
    void getMyAssignmentsReturnsSeedTask() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tasks/my-assignments")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode assignments = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(assignments.isArray()).isTrue();
        assertThat(assignments.size()).isGreaterThanOrEqualTo(1);
        assertThat(assignments.get(0).get("status").asText()).isEqualTo("TODO");
    }

    @Test
    void updateAssignmentStatusToInProgress() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/assignments/" + seededAssignmentId + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // -----------------------------------------------------------------------
    // Requests — view, create, delete
    // -----------------------------------------------------------------------

    @Test
    void getMyRequestsReturnsSeededPendingRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/requests/my-requests")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode requests = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(requests.isArray()).isTrue();
        assertThat(requests.size()).isGreaterThanOrEqualTo(1);

        boolean foundPending = false;
        for (JsonNode req : requests) {
            if ("PENDING".equals(req.get("status").asText())) {
                foundPending = true;
                break;
            }
        }
        assertThat(foundPending).as("At least one PENDING request must be present").isTrue();
    }

    @Test
    void createAndDeleteWfhRequest() throws Exception {
        // Use the OFFICE day found in setUp — WFH is only valid on OFFICE-scheduled days
        String body = objectMapper.writeValueAsString(Map.of(
                "type", "WFH",
                "startDate", wfhDate.toString(),
                "endDate", wfhDate.toString(),
                "reason", "Working from home test"
        ));

        // CREATE — expect 200 with the new request in the body
        MvcResult createResult = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("WFH"))
                .andReturn();

        Long newRequestId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // DELETE — own pending request, expect 204
        mockMvc.perform(delete("/api/requests/" + newRequestId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNoContent());
    }

    // -----------------------------------------------------------------------
    // Preferences — save and retrieve
    // -----------------------------------------------------------------------

    @Test
    void setAndGetPreferences() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("preferredDays", List.of("MONDAY", "WEDNESDAY")));

        // POST — save preferences
        mockMvc.perform(post("/api/preferences/online-days")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // GET — verify saved days are returned
        MvcResult getResult = mockMvc.perform(get("/api/preferences/online-days")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode prefs = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(prefs.has("preferredDays")).isTrue();
        assertThat(prefs.get("preferredDays").size()).isEqualTo(2);
    }
}
