package com.example.hybridflow;

import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.repository.MeetingRepository;
import com.example.hybridflow.repository.PlanningPolicyRepository;
import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 0 — Startup & Seed Verification
 *
 * Verifies that the application context loads cleanly and every required
 * seed record is present after the CommandLineRunner runs.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SeedDataIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired PlanningPolicyRepository planningPolicyRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskAssignmentRepository taskAssignmentRepository;
    @Autowired MeetingRepository meetingRepository;
    @Autowired RequestRepository requestRepository;

    // -----------------------------------------------------------------------
    // Context load
    // -----------------------------------------------------------------------

    @Test
    void contextLoads() {
        // Passes if the Spring context starts without errors.
        // Hibernate schema creation and the CommandLineRunner must both complete
        // without throwing an exception.
    }

    // -----------------------------------------------------------------------
    // Users
    // -----------------------------------------------------------------------

    @Test
    void sevenUsersSeeded() {
        assertThat(userRepository.count())
                .as("Expected 7 users: 1 HR + 2 managers + 4 employees")
                .isEqualTo(7);
    }

    @Test
    void allSeededUsersAreEnabled() {
        long enabledCount = userRepository.findAll().stream()
                .filter(u -> u.isEnabled())
                .count();
        assertThat(enabledCount)
                .as("All 7 seeded users must be enabled")
                .isEqualTo(7);
    }

    // -----------------------------------------------------------------------
    // Teams
    // -----------------------------------------------------------------------

    @Test
    void twoTeamsSeeded() {
        assertThat(teamRepository.count())
                .as("Expected 2 teams: Backend Devs and UI/UX Design")
                .isEqualTo(2);
    }

    @Test
    void teamNamesMatchSpec() {
        List<String> names = teamRepository.findAll().stream()
                .map(t -> t.getName())
                .sorted()
                .toList();
        assertThat(names).containsExactly("Backend Devs", "UI/UX Design");
    }

    // -----------------------------------------------------------------------
    // Schedules
    // -----------------------------------------------------------------------

    @Test
    void twoPublishedSchedulesSeeded() {
        long publishedCount = scheduleRepository.findAll().stream()
                .filter(s -> s.isPublished())
                .count();
        assertThat(publishedCount)
                .as("Expected 2 published schedules, one per team")
                .isEqualTo(2);
    }

    @Test
    void publishedSchedulesCoversToday() {
        LocalDate today = LocalDate.now();
        boolean todayIsCovered = scheduleRepository.findAll().stream()
                .filter(s -> s.isPublished())
                .anyMatch(s ->
                        !s.getStartDate().isAfter(today) &&
                        !s.getEndDate().isBefore(today));
        assertThat(todayIsCovered)
                .as("At least one published schedule must cover today's date")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // Planning Policy
    // -----------------------------------------------------------------------

    @Test
    void onePlanningPolicySeeded() {
        assertThat(planningPolicyRepository.count())
                .as("Expected exactly 1 planning policy")
                .isEqualTo(1);
    }

    @Test
    void planningPolicyNameMatchesSpec() {
        String name = planningPolicyRepository.findAll().get(0).getName();
        assertThat(name).isEqualTo("Standard Hybrid Policy");
    }

    // -----------------------------------------------------------------------
    // Task & TaskAssignment
    // -----------------------------------------------------------------------

    @Test
    void oneTaskSeeded() {
        assertThat(taskRepository.count())
                .as("Expected exactly 1 task")
                .isEqualTo(1);
    }

    @Test
    void oneTaskAssignmentSeeded() {
        assertThat(taskAssignmentRepository.count())
                .as("Expected exactly 1 task assignment")
                .isEqualTo(1);
    }

    @Test
    void taskAssignmentStatusIsTodo() {
        var status = taskAssignmentRepository.findAll().get(0).getStatus();
        assertThat(status.name()).isEqualTo("TODO");
    }

    // -----------------------------------------------------------------------
    // Meeting
    // -----------------------------------------------------------------------

    @Test
    void oneMeetingSeeded() {
        assertThat(meetingRepository.count())
                .as("Expected exactly 1 meeting")
                .isEqualTo(1);
    }

    @Test
    void meetingTitleMatchesSpec() {
        String title = meetingRepository.findAll().get(0).getTitle();
        assertThat(title).isEqualTo("Sprint Planning");
    }

    // -----------------------------------------------------------------------
    // Request
    // -----------------------------------------------------------------------

    @Test
    void oneRequestSeeded() {
        assertThat(requestRepository.count())
                .as("Expected exactly 1 request")
                .isEqualTo(1);
    }

    @Test
    void seededRequestIsPending() {
        var status = requestRepository.findAll().get(0).getStatus();
        assertThat(status)
                .as("Seeded request must be PENDING")
                .isEqualTo(RequestStatus.PENDING);
    }
}
