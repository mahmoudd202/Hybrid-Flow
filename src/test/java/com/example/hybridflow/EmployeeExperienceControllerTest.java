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
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.OfficeRepository;

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
    @Autowired TaskRepository taskRepository;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired ScheduleEntryRepository scheduleEntryRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired OfficeRepository officeRepository;

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

    @Test
    void getMyMeetingsReturnsMultiTeamOnlineMeetingWithNullOfficeAndAllTeamNames() throws Exception {
        // 1. Log in as manager.a to create the meeting
        String loginBody = objectMapper.writeValueAsString(
                Map.of("email", "manager.a@techflow.com", "password", "password123"));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String managerToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // 2. Resolve team and office IDs
        Long backendDevsTeamId = teamRepository.findAll().stream()
                .filter(t -> "Backend Devs".equals(t.getName()))
                .findFirst().orElseThrow().getId();
        Long uiuxTeamId = teamRepository.findAll().stream()
                .filter(t -> "UI/UX Design".equals(t.getName()))
                .findFirst().orElseThrow().getId();
        Long officeId = officeRepository.findAll().get(0).getId();

        // 3. Create a multi-team ONLINE meeting
        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "Cross-Team Sync",
                "startTime", java.time.LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0).toString(),
                "endTime", java.time.LocalDateTime.now().plusDays(2).withHour(16).withMinute(0).withSecond(0).withNano(0).toString(),
                "officeId", officeId,
                "type", "ONLINE",
                "participatingTeamIds", List.of(backendDevsTeamId, uiuxTeamId)
        ));

        mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cross-Team Sync"))
                .andExpect(jsonPath("$.officeName").isEmpty()); // Should be null in response for ONLINE meetings

        // 4. Retrieve schedule for employee dev1 (who is in Backend Devs)
        MvcResult scheduleResult = mockMvc.perform(get("/api/meetings/my-schedule")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode meetings = objectMapper.readTree(scheduleResult.getResponse().getContentAsString());
        assertThat(meetings.isArray()).isTrue();

        JsonNode crossTeamSyncMeeting = null;
        for (JsonNode meeting : meetings) {
            if ("Cross-Team Sync".equals(meeting.get("title").asText())) {
                crossTeamSyncMeeting = meeting;
                break;
            }
        }

        assertThat(crossTeamSyncMeeting).as("The Cross-Team Sync meeting must be present").isNotNull();
        assertThat(crossTeamSyncMeeting.get("officeName").isNull()).isTrue(); // Double check it is null

        // Verify that BOTH teams are present in participatingTeamNames (No collection truncation!)
        JsonNode teamNamesNode = crossTeamSyncMeeting.get("participatingTeamNames");
        assertThat(teamNamesNode).isNotNull();
        assertThat(teamNamesNode.isArray()).isTrue();
        
        java.util.Set<String> teamNames = new java.util.HashSet<>();
        teamNamesNode.forEach(node -> teamNames.add(node.asText()));

        assertThat(teamNames).containsExactlyInAnyOrder("Backend Devs", "UI/UX Design");
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

    @Test
    void createAndDeleteBacklogTask() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "My Backlog Task",
                "description", "A task for my backlog",
                "dueDate", java.time.LocalDateTime.now().plusDays(5).toString()
        ));

        // Create backlog task as dev1
        MvcResult createResult = mockMvc.perform(post("/api/tasks/backlog")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long assignmentId = response.get("assignmentId").asLong();
        long taskId = response.get("taskId").asLong();

        // Attempt to delete it as manager.a -> expect 403 Forbidden
        String managerLoginBody = objectMapper.writeValueAsString(
                Map.of("email", "manager.a@techflow.com", "password", "password123"));

        MvcResult managerLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(managerLoginBody))
                .andExpect(status().isOk())
                .andReturn();

        String managerToken = objectMapper.readTree(managerLoginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(delete("/api/tasks/backlog/" + assignmentId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Update backlog task assignment status to IN_PROGRESS
        String updateStatusBody = objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"));
        mockMvc.perform(patch("/api/tasks/assignments/" + assignmentId + "/status")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStatusBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Delete it as dev1 (the creator/assignee) -> expect 204 No Content even if status is IN_PROGRESS
        mockMvc.perform(delete("/api/tasks/backlog/" + assignmentId)
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isNoContent());

        // Verify task assignment and task are gone
        assertThat(taskAssignmentRepository.findById(assignmentId)).isEmpty();
        assertThat(taskRepository.findById(taskId)).isEmpty();
    }

    @Test
    void editOwnTaskAndFailOnOthers() throws Exception {
        // Find a task created by someone else (e.g. manager.a)
        Long managerTaskId = taskRepository.findAll().stream()
                .filter(t -> !"dev1@techflow.com".equals(t.getCreatedBy().getEmail()))
                .findFirst().orElseThrow().getId();

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "Hacked Title",
                "description", "Hacked description",
                "dueDate", wfhDate.atTime(12, 0).toString()
        ));

        // Attempt to edit others' task as dev1 -> expect 403 Forbidden
        mockMvc.perform(put("/api/tasks/" + managerTaskId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        // Create backlog task as dev1
        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "My Backlog Task to Edit",
                "description", "Original description",
                "dueDate", wfhDate.atTime(12, 0).toString()
        ));

        MvcResult createResult = mockMvc.perform(post("/api/tasks/backlog")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long ownTaskId = response.get("taskId").asLong();

        // Edit own task as dev1 -> expect 200 OK
        String ownUpdateBody = objectMapper.writeValueAsString(Map.of(
                "title", "Updated Own Task Title",
                "description", "Updated description",
                "dueDate", wfhDate.atTime(12, 0).toString()
        ));

        mockMvc.perform(put("/api/tasks/" + ownTaskId)
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownUpdateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.title").value("Updated Own Task Title"));
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

    @Test
    void createRequestExceedsMonthlyLimitThrowsException() throws Exception {
        com.example.hybridflow.entity.Company company = userRepository.findById(dev1Id).orElseThrow().getCompany();

        for (int i = 0; i < 5; i++) {
            com.example.hybridflow.entity.Request dummy = new com.example.hybridflow.entity.Request();
            dummy.setRequester(userRepository.findById(dev1Id).orElseThrow());
            dummy.setCompany(company);
            dummy.setType(com.example.hybridflow.entity.RequestType.WFH);
            dummy.setStartDate(LocalDate.now().plusDays(100 + i));
            dummy.setEndDate(LocalDate.now().plusDays(100 + i));
            dummy.setReason("Dummy request " + i);
            dummy.setStatus(com.example.hybridflow.entity.RequestStatus.PENDING);
            requestRepository.save(dummy);
        }

        String body = objectMapper.writeValueAsString(Map.of(
                "type", "WFH",
                "startDate", wfhDate.toString(),
                "endDate", wfhDate.toString(),
                "reason", "This should fail because limit is 5"
        ));

        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You have reached the limit of 5 requests per month."));
    }
}
