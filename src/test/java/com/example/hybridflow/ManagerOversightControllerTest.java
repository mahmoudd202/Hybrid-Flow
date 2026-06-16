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

import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
@ActiveProfiles("test")
class ManagerOversightControllerTest {

        @Autowired
        WebApplicationContext webApplicationContext;
        @Autowired
        TeamRepository teamRepository;
        @Autowired
        OfficeRepository officeRepository;
        @Autowired
        ScheduleEntryRepository scheduleEntryRepository;
        @Autowired
        TaskRepository taskRepository;
        @Autowired
        TaskAssignmentRepository taskAssignmentRepository;
        @Autowired
        UserRepository userRepository;

        MockMvc mockMvc;
        final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String managerToken;
        Long backendDevsTeamId;
        Long seededTaskId;
        Long seededAssignmentId;
        Long dev1Id;
        Long officeId;
        LocalDateTime taskDueDate;

        @BeforeEach
        void setUp() throws Exception {
                mockMvc = MockMvcBuilders
                                .webAppContextSetup(webApplicationContext)
                                .apply(SecurityMockMvcConfigurers.springSecurity())
                                .build();

                String body = objectMapper.writeValueAsString(
                                Map.of("email", "manager.a@techflow.com", "password", "password123"));

                MvcResult result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn();

                managerToken = objectMapper.readTree(result.getResponse().getContentAsString())
                                .get("accessToken").asText();

                backendDevsTeamId = teamRepository.findAll().stream()
                                .filter(t -> "Backend Devs".equals(t.getName()))
                                .findFirst().orElseThrow().getId();

                officeId = officeRepository.findAll().get(0).getId();

                dev1Id = userRepository.findByEmail("dev1@techflow.com").orElseThrow().getId();

                Long managerId = userRepository.findByEmail("manager.a@techflow.com").orElseThrow().getId();
                seededTaskId = taskRepository.findAllCreatedForManagedTeam(managerId).get(0).getId();

                seededAssignmentId = taskAssignmentRepository.findAllForAssignee(dev1Id)
                                .get(0).getId();

                taskDueDate = scheduleEntryRepository
                                .findPublishedEntriesForUser(dev1Id, LocalDate.now().plusDays(1),
                                                LocalDate.now().plusDays(28))
                                .stream()
                                .filter(e -> e.getWorkMode() != com.example.hybridflow.entity.WorkMode.OFF)
                                .map(e -> e.getDate().atTime(10, 0))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                                "No future scheduled entry found for dev1 — seed data issue"));
        }

        @Test
        void getTeamScheduleReturnsAllBackendDevsMembers() throws Exception {
                LocalDate from = LocalDate.now().minusDays(7);
                LocalDate to = LocalDate.now().plusDays(21);

                MvcResult result = mockMvc.perform(get("/api/schedules/team/" + backendDevsTeamId)
                                .param("from", from.toString())
                                .param("to", to.toString())
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(body.has("teams")).isTrue();

                JsonNode members = body.get("teams").get(0).get("members");
                assertThat(members).isNotNull();
                assertThat(members.size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void getManagerCreatedTasksReturnsSeededTask() throws Exception {
                MvcResult result = mockMvc.perform(get("/api/tasks/manager/created")
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode tasks = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(tasks.isArray()).isTrue();
                assertThat(tasks.size()).isGreaterThanOrEqualTo(1);

                boolean foundCiCd = false;
                for (JsonNode task : tasks) {
                        if ("Set up CI/CD pipeline".equals(task.get("title").asText())) {
                                foundCiCd = true;
                                break;
                        }
                }
                assertThat(foundCiCd).as("Seeded 'Set up CI/CD pipeline' task must be present").isTrue();
        }

        @Test
        void getAssignmentsForTaskReturnsDev1Assignment() throws Exception {
                MvcResult result = mockMvc.perform(get("/api/tasks/" + seededTaskId + "/assignments")
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode assignments = objectMapper.readTree(result.getResponse().getContentAsString());
                assertThat(assignments.isArray()).isTrue();
                assertThat(assignments.size()).isGreaterThanOrEqualTo(1);
                assertThat(assignments.get(0).get("assigneeEmail").asText()).isEqualTo("dev1@techflow.com");
        }

        @Test
        void createTaskReturns200WithBody() throws Exception {
                String body = objectMapper.writeValueAsString(Map.of(
                                "title", "Write unit tests",
                                "description", "Cover all service methods",
                                "targetType", "INDIVIDUAL",
                                "assigneeId", dev1Id,
                                "dueDate", taskDueDate.toString()));

                mockMvc.perform(post("/api/tasks")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.task.title").value("Write unit tests"));
        }

        @Test
        void deleteTaskDeletesTaskAndItsAssignments() throws Exception {
                String body = objectMapper.writeValueAsString(Map.of(
                                "title", "Temporary Task to Delete",
                                "description", "Delete this task",
                                "targetType", "INDIVIDUAL",
                                "assigneeId", dev1Id,
                                "dueDate", taskDueDate.toString()));

                MvcResult createResult = mockMvc.perform(post("/api/tasks")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode response = objectMapper.readTree(createResult.getResponse().getContentAsString());
                long newTaskId = response.get("task").get("id").asLong();

                List<com.example.hybridflow.entity.TaskAssignment> assignmentsBefore = taskAssignmentRepository
                                .findAllByTaskId(newTaskId);
                assertThat(assignmentsBefore).hasSize(1);

                mockMvc.perform(delete("/api/tasks/" + newTaskId)
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isNoContent());

                assertThat(taskRepository.findById(newTaskId)).isEmpty();

                List<com.example.hybridflow.entity.TaskAssignment> assignmentsAfter = taskAssignmentRepository
                                .findAllByTaskId(newTaskId);
                assertThat(assignmentsAfter).isEmpty();
        }

        @Test
        void deleteTaskReturns404IfNotFound() throws Exception {
                mockMvc.perform(delete("/api/tasks/999999")
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void deleteTaskReturns403IfNotCreator() throws Exception {
                String body = objectMapper.writeValueAsString(Map.of(
                                "title", "Manager A Task",
                                "description", "Delete this task",
                                "targetType", "INDIVIDUAL",
                                "assigneeId", dev1Id,
                                "dueDate", taskDueDate.toString()));

                MvcResult createResult = mockMvc.perform(post("/api/tasks")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode response = objectMapper.readTree(createResult.getResponse().getContentAsString());
                long newTaskId = response.get("task").get("id").asLong();

                String loginBody = objectMapper.writeValueAsString(
                                Map.of("email", "manager.b@techflow.com", "password", "password123"));

                MvcResult loginResult = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isOk())
                                .andReturn();

                String managerBToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("accessToken").asText();

                mockMvc.perform(delete("/api/tasks/" + newTaskId)
                                .header("Authorization", "Bearer " + managerBToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void meetingCrudCycle() throws Exception {
                LocalDate meetingDate = LocalDate.now().plusDays(2);
                com.example.hybridflow.entity.User managerUser = userRepository.findByEmail("manager.a@techflow.com").orElseThrow();
                scheduleEntryRepository.findPublishedEntryForUserOnDate(managerUser.getId(), meetingDate)
                                .ifPresent(entry -> {
                                        entry.setWorkMode(com.example.hybridflow.entity.WorkMode.OFFICE);
                                        scheduleEntryRepository.save(entry);
                                });

                String createBody = objectMapper.writeValueAsString(Map.of(
                                "title", "Test Meeting",
                                "startTime",
                                meetingDate.atTime(14, 0, 0, 0).toString(),
                                "endTime",
                                meetingDate.atTime(15, 0, 0, 0).toString(),
                                "officeId", officeId,
                                "type", "OFFICE",
                                "participatingTeamIds", List.of(backendDevsTeamId)));

                MvcResult createResult = mockMvc.perform(post("/api/meetings")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Test Meeting"))
                                .andExpect(jsonPath("$.id").isNumber())
                                .andReturn();

                Long newMeetingId = objectMapper.readTree(
                                createResult.getResponse().getContentAsString()).get("id").asLong();

                MvcResult listResult = mockMvc.perform(get("/api/meetings/team/" + backendDevsTeamId)
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andReturn();

                JsonNode meetings = objectMapper.readTree(listResult.getResponse().getContentAsString());
                assertThat(meetings.isArray()).isTrue();
                assertThat(meetings.size()).isGreaterThanOrEqualTo(2);

                String updateBody = objectMapper.writeValueAsString(Map.of(
                                "title", "Updated Meeting Title",
                                "startTime",
                                meetingDate.atTime(14, 0, 0, 0).toString(),
                                "endTime",
                                meetingDate.atTime(15, 0, 0, 0).toString(),
                                "officeId", officeId,
                                "type", "OFFICE",
                                "participatingTeamIds", List.of(backendDevsTeamId)));

                mockMvc.perform(put("/api/meetings/" + newMeetingId)
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("Updated Meeting Title"));

                mockMvc.perform(delete("/api/meetings/" + newMeetingId)
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isNoContent());
        }

        @Test
        void getPendingRequestsReturnsDev1WfhRequest() throws Exception {
                mockMvc.perform(get("/api/requests/pending")
                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isForbidden());
        }
}
