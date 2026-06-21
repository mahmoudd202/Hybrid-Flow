package com.example.hybridflow;

import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Meeting;
import com.example.hybridflow.entity.MeetingType;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.PlanningPolicy;
import com.example.hybridflow.entity.Request;
import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.entity.RequestType;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.Task;
import com.example.hybridflow.entity.TaskAssignment;
import com.example.hybridflow.entity.TaskAssignmentStatus;
import com.example.hybridflow.entity.TaskTargetType;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.repository.CompanyRepository;
import com.example.hybridflow.repository.MeetingRepository;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.PlanningPolicyRepository;
import com.example.hybridflow.repository.RequestRepository;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Configuration
public class TestDataSeeder {

    @Bean
    public CommandLineRunner commandLineRunner(
            UserRepository userRepository,
            TeamRepository teamRepository,
            CompanyRepository companyRepository,
            OfficeRepository officeRepository,
            ScheduleRepository scheduleRepository,
            ScheduleEntryRepository scheduleEntryRepository,
            PlanningPolicyRepository planningPolicyRepository,
            TaskRepository taskRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            MeetingRepository meetingRepository,
            RequestRepository requestRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Create company
            Company company = new Company();
            company.setCompanyName("TechFlow Corp");
            company = companyRepository.save(company);

            // 2. Create office
            Office office = new Office();
            office.setName("Main HQ - New York");

            // If your Office entity has maxCapacity as required, keep this.
            // If not, remove this line.
            office.setMaxCapacity(50);

            office.setCompany(company);
            office = officeRepository.save(office);

            // 3. Create Team A
            Team teamA = new Team();
            teamA.setName("Backend Devs");
            teamA.setCompany(company);
            teamA.setOffice(office);
            teamA = teamRepository.save(teamA);

            // 4. Create Team B
            Team teamB = new Team();
            teamB.setName("UI/UX Design");
            teamB.setCompany(company);
            teamB.setOffice(office);
            teamB = teamRepository.save(teamB);

            String commonPassword = passwordEncoder.encode("password123");

            // 5. Seed Team A users
            User managerA = new User();
            managerA.setEmail("manager.a@techflow.com");
            managerA.setPassword(commonPassword);
            managerA.setRole(Role.MANAGER);
            managerA.setCompany(company);
            managerA.setTeam(teamA);
            managerA.setEnabled(true);
            managerA = userRepository.save(managerA);

            User dev1 = new User();
            dev1.setEmail("dev1@techflow.com");
            dev1.setPassword(commonPassword);
            dev1.setRole(Role.EMPLOYEE);
            dev1.setCompany(company);
            dev1.setTeam(teamA);
            dev1.setEnabled(true);
            dev1 = userRepository.save(dev1);

            User dev2 = new User();
            dev2.setEmail("dev2@techflow.com");
            dev2.setPassword(commonPassword);
            dev2.setRole(Role.EMPLOYEE);
            dev2.setCompany(company);
            dev2.setTeam(teamA);
            dev2.setEnabled(true);
            dev2 = userRepository.save(dev2);

            teamA.setManager(managerA);
            teamA = teamRepository.save(teamA);

            // 6. Seed Team B users
            User managerB = new User();
            managerB.setEmail("manager.b@techflow.com");
            managerB.setPassword(commonPassword);
            managerB.setRole(Role.MANAGER);
            managerB.setCompany(company);
            managerB.setTeam(teamB);
            managerB.setEnabled(true);
            managerB = userRepository.save(managerB);

            User designer1 = new User();
            designer1.setEmail("designer1@techflow.com");
            designer1.setPassword(commonPassword);
            designer1.setRole(Role.EMPLOYEE);
            designer1.setCompany(company);
            designer1.setTeam(teamB);
            designer1.setEnabled(true);
            designer1 = userRepository.save(designer1);

            User designer2 = new User();
            designer2.setEmail("designer2@techflow.com");
            designer2.setPassword(commonPassword);
            designer2.setRole(Role.EMPLOYEE);
            designer2.setCompany(company);
            designer2.setTeam(teamB);
            designer2.setEnabled(true);
            designer2 = userRepository.save(designer2);

            teamB.setManager(managerB);
            teamB = teamRepository.save(teamB);

            User hrUser = new User();
            hrUser.setEmail("hr@techflow.com");
            hrUser.setPassword(commonPassword);
            hrUser.setRole(Role.HR);
            hrUser.setCompany(company);
            hrUser.setEnabled(true);
            userRepository.save(hrUser);

            LocalDate startDate = LocalDate.now().with(DayOfWeek.MONDAY);
            LocalDate endDate = startDate.plusWeeks(4).minusDays(1);

            Schedule backendSchedule = new Schedule();
            backendSchedule.setTeam(teamA);
            backendSchedule.setOffice(office);
            backendSchedule.setStartDate(startDate);
            backendSchedule.setEndDate(endDate);
            backendSchedule.setPublished(true);
            backendSchedule = scheduleRepository.save(backendSchedule);

            Schedule designSchedule = new Schedule();
            designSchedule.setTeam(teamB);
            designSchedule.setOffice(office);
            designSchedule.setStartDate(startDate);
            designSchedule.setEndDate(endDate);
            designSchedule.setPublished(true);
            designSchedule = scheduleRepository.save(designSchedule);

            Random random = new Random();

            createRandomScheduleEntries(
                    scheduleEntryRepository,
                    backendSchedule,
                    List.of(managerA, dev1, dev2),
                    startDate,
                    endDate,
                    random);

            createRandomScheduleEntries(
                    scheduleEntryRepository,
                    designSchedule,
                    List.of(managerB, designer1, designer2),
                    startDate,
                    endDate,
                    random);

            PlanningPolicy policy = new PlanningPolicy();
            policy.setCompany(company);
            policy.setName("Standard Hybrid Policy");
            policy.setWorkingDaysPerWeek(5);
            policy.setMinOfficeDaysPerWeek(2);
            policy.setMaxOfficeDaysPerWeek(3);
            policy.setMaxConsecutiveOfficeDays(3);
            policy.setMinTeamSharedDays(1);
            policy.setCoPresenceThresholdPercentagePerDay(50);
            planningPolicyRepository.save(policy);

            Task task = new Task();
            task.setTitle("Set up CI/CD pipeline");
            task.setDescription("Configure GitHub Actions for automated build and deployment.");
            task.setDueDate(LocalDateTime.now().plusDays(7));
            task.setTargetType(TaskTargetType.INDIVIDUAL);
            task.setCreatedBy(managerA);
            task.setCompany(company);
            task.setTeam(teamA);
            task = taskRepository.save(task);

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignee(dev1);
            assignment.setStatus(TaskAssignmentStatus.TODO);
            assignment.setAssignedAt(LocalDateTime.now());
            taskAssignmentRepository.save(assignment);

            Meeting meeting = new Meeting();
            meeting.setTitle("Sprint Planning");
            meeting.setStartTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
            meeting.setEndTime(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0));
            meeting.setHost(managerA);
            meeting.setOffice(office);
            meeting.setType(MeetingType.OFFICE);
            meeting.setParticipatingTeams(List.of(teamA));
            meetingRepository.save(meeting);

            Request wfhRequest = new Request();
            wfhRequest.setRequester(dev1);
            wfhRequest.setCompany(company);
            wfhRequest.setType(RequestType.WFH);
            wfhRequest.setStatus(RequestStatus.PENDING);
            LocalDate seededRequestDate = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1).plusDays(3);
            wfhRequest.setStartDate(seededRequestDate);
            wfhRequest.setEndDate(seededRequestDate);
            wfhRequest.setReason("Doctor appointment in the morning, working from home in the afternoon.");
            requestRepository.save(wfhRequest);

            System.out.println(">>> Database seeded successfully.");
            System.out.println(">>> Company: TechFlow Corp");
            System.out.println(">>> Office: Main HQ - New York");
            System.out.println(">>> Teams: Backend Devs, UI/UX Design");
            System.out.println(">>> Users:");
            System.out.println("    HR: hr@techflow.com / password123");
            System.out.println("    Manager A: manager.a@techflow.com / password123");
            System.out.println("    Manager B: manager.b@techflow.com / password123");
            System.out.println(
                    "    Employees: dev1@techflow.com, dev2@techflow.com, designer1@techflow.com, designer2@techflow.com / password123");
            System.out.println(">>> Random published schedule entries created for managers and employees only.");
            System.out.println(">>> HR has no schedule entries.");
        };
    }

    private static void createRandomScheduleEntries(
            ScheduleEntryRepository scheduleEntryRepository,
            Schedule schedule,
            List<User> users,
            LocalDate startDate,
            LocalDate endDate,
            Random random) {
        for (User user : users) {
            LocalDate currentDate = startDate;

            while (!currentDate.isAfter(endDate)) {
                DayOfWeek dayOfWeek = currentDate.getDayOfWeek();

                // Skip weekends.
                if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                    ScheduleEntry entry = new ScheduleEntry();
                    entry.setSchedule(schedule);
                    entry.setUser(user);
                    entry.setDate(currentDate);

                    WorkMode workMode = random.nextBoolean()
                            ? WorkMode.OFFICE
                            : WorkMode.ONLINE;

                    entry.setWorkMode(workMode);

                    scheduleEntryRepository.save(entry);
                }

                currentDate = currentDate.plusDays(1);
            }
        }
    }
}
