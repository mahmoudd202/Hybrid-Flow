package com.example.hybridflow;

import com.example.hybridflow.entity.AuthProvider;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.UserProfile;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.repository.CompanyRepository;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserProfileRepository;
import com.example.hybridflow.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@SpringBootApplication
@EnableScheduling
public class HybridFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(HybridFlowApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            TeamRepository teamRepository,
            CompanyRepository companyRepository,
            OfficeRepository officeRepository,
            ScheduleRepository scheduleRepository,
            ScheduleEntryRepository scheduleEntryRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {

            /*
             * Prevent duplicate seed data on every application restart.
             * This is important if spring.jpa.hibernate.ddl-auto is update.
             */
            if (userRepository.existsByEmail("hr@techflow.com")) {
                System.out.println(">>> Seed data already exists. Skipping CommandLineRunner seed.");
                return;
            }

            // 1. Create company
            Company company = new Company();
            company.setCompanyName("TechFlow Corp");
            company = companyRepository.save(company);

            // 2. Create office
            Office office = new Office();
            office.setName("Main HQ - New York");
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
            User managerA = createUser(
                    "manager.a@techflow.com",
                    commonPassword,
                    Role.MANAGER,
                    company,
                    teamA
            );
            managerA = userRepository.save(managerA);

            User dev1 = createUser(
                    "dev1@techflow.com",
                    commonPassword,
                    Role.EMPLOYEE,
                    company,
                    teamA
            );
            dev1 = userRepository.save(dev1);

            User dev2 = createUser(
                    "dev2@techflow.com",
                    commonPassword,
                    Role.EMPLOYEE,
                    company,
                    teamA
            );
            dev2 = userRepository.save(dev2);

            teamA.setManager(managerA);
            teamRepository.save(teamA);

            // 6. Seed Team B users
            User managerB = createUser(
                    "manager.b@techflow.com",
                    commonPassword,
                    Role.MANAGER,
                    company,
                    teamB
            );
            managerB = userRepository.save(managerB);

            User designer1 = createUser(
                    "designer1@techflow.com",
                    commonPassword,
                    Role.EMPLOYEE,
                    company,
                    teamB
            );
            designer1 = userRepository.save(designer1);

            User designer2 = createUser(
                    "designer2@techflow.com",
                    commonPassword,
                    Role.EMPLOYEE,
                    company,
                    teamB
            );
            designer2 = userRepository.save(designer2);

            teamB.setManager(managerB);
            teamRepository.save(teamB);

            // 7. Seed HR user
            User hrUser = createUser(
                    "hr@techflow.com",
                    commonPassword,
                    Role.HR,
                    company,
                    null
            );
            hrUser = userRepository.save(hrUser);

            // 8. Create profiles for seeded users
            createProfile(userProfileRepository, managerA, "Manager", "Alpha", "American");
            createProfile(userProfileRepository, dev1, "Dev", "One", "American");
            createProfile(userProfileRepository, dev2, "Dev", "Two", "American");
            createProfile(userProfileRepository, managerB, "Manager", "Beta", "American");
            createProfile(userProfileRepository, designer1, "Designer", "One", "American");
            createProfile(userProfileRepository, designer2, "Designer", "Two", "American");
            createProfile(userProfileRepository, hrUser, "HR", "Admin", "American");

            /*
             * 9. Seed published read-only schedules.
             *
             * These are sample schedules only.
             * Do not build schedule creation/publishing UI yet.
             */
            LocalDate monday = LocalDate.now()
                    .with(java.time.DayOfWeek.MONDAY);

            Schedule backendSchedule = createPublishedSchedule(
                    scheduleRepository,
                    teamA,
                    office,
                    monday,
                    monday.plusDays(4)
            );

            Schedule designSchedule = createPublishedSchedule(
                    scheduleRepository,
                    teamB,
                    office,
                    monday,
                    monday.plusDays(4)
            );

            // Team A schedule entries
            createScheduleEntry(scheduleEntryRepository, backendSchedule, managerA, monday, WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, managerA, monday.plusDays(1), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, managerA, monday.plusDays(2), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, managerA, monday.plusDays(3), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, managerA, monday.plusDays(4), WorkMode.OFFICE);

            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev1, monday, WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev1, monday.plusDays(1), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev1, monday.plusDays(2), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev1, monday.plusDays(3), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev1, monday.plusDays(4), WorkMode.ONLINE);

            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev2, monday, WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev2, monday.plusDays(1), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev2, monday.plusDays(2), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev2, monday.plusDays(3), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, backendSchedule, dev2, monday.plusDays(4), WorkMode.OFFICE);

            // Team B schedule entries
            createScheduleEntry(scheduleEntryRepository, designSchedule, managerB, monday, WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, managerB, monday.plusDays(1), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, managerB, monday.plusDays(2), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, managerB, monday.plusDays(3), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, managerB, monday.plusDays(4), WorkMode.ONLINE);

            createScheduleEntry(scheduleEntryRepository, designSchedule, designer1, monday, WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer1, monday.plusDays(1), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer1, monday.plusDays(2), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer1, monday.plusDays(3), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer1, monday.plusDays(4), WorkMode.OFFICE);

            createScheduleEntry(scheduleEntryRepository, designSchedule, designer2, monday, WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer2, monday.plusDays(1), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer2, monday.plusDays(2), WorkMode.ONLINE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer2, monday.plusDays(3), WorkMode.OFFICE);
            createScheduleEntry(scheduleEntryRepository, designSchedule, designer2, monday.plusDays(4), WorkMode.ONLINE);

            System.out.println(">>> Database seeded successfully.");
            System.out.println(">>> Company: TechFlow Corp");
            System.out.println(">>> Office: Main HQ - New York");
            System.out.println(">>> Teams: Backend Devs, UI/UX Design");
            System.out.println(">>> Users:");
            System.out.println("    HR: hr@techflow.com / password123");
            System.out.println("    Manager A: manager.a@techflow.com / password123");
            System.out.println("    Manager B: manager.b@techflow.com / password123");
            System.out.println("    Employees: dev1@techflow.com, dev2@techflow.com, designer1@techflow.com, designer2@techflow.com / password123");
            System.out.println(">>> Published sample schedules created for current week.");
        };
    }

    private static User createUser(
            String email,
            String encodedPassword,
            Role role,
            Company company,
            Team team
    ) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setCompany(company);
        user.setTeam(team);
        user.setEnabled(true);
        user.setProvider(AuthProvider.LOCAL);
        return user;
    }

    private static void createProfile(
            UserProfileRepository userProfileRepository,
            User user,
            String firstName,
            String lastName,
            String nationality
    ) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setDateOfBirth(LocalDate.of(1998, 1, 1));
        profile.setNationality(nationality);
        userProfileRepository.save(profile);
    }

    private static Schedule createPublishedSchedule(
            ScheduleRepository scheduleRepository,
            Team team,
            Office office,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Schedule schedule = new Schedule();
        schedule.setTeam(team);
        schedule.setOffice(office);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setPublished(true);
        return scheduleRepository.save(schedule);
    }

    private static void createScheduleEntry(
            ScheduleEntryRepository scheduleEntryRepository,
            Schedule schedule,
            User user,
            LocalDate date,
            WorkMode workMode
    ) {
        ScheduleEntry entry = new ScheduleEntry();
        entry.setSchedule(schedule);
        entry.setUser(user);
        entry.setDate(date);
        entry.setWorkMode(workMode);
        scheduleEntryRepository.save(entry);
    }
}