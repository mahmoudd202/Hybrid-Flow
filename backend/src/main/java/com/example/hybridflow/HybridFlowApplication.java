package com.example.hybridflow;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.repository.CompanyRepository;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;

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
        TeamRepository teamRepository,
        CompanyRepository companyRepository,
        OfficeRepository officeRepository,
        PasswordEncoder passwordEncoder) {
    return args -> {
        // 1. Create a Company
        Company company = new Company();
        company.setCompanyName("TechFlow Corp");
        company = companyRepository.save(company);

        // 2. Create an Office
        Office office = new Office();
        office.setName("Main HQ - New York");
        office.setCompany(company);
        office = officeRepository.save(office);

        // 3. Create Team A (Development)
        Team teamA = new Team();
        teamA.setName("Backend Devs");
        teamA.setCompany(company);
        teamA.setOffice(office);
        teamA = teamRepository.save(teamA);

        // 4. Create Team B (Design)
        Team teamB = new Team();
        teamB.setName("UI/UX Design");
        teamB.setCompany(company);
        teamB.setOffice(office);
        teamB = teamRepository.save(teamB);

        String commonPassword = passwordEncoder.encode("password123");

        // 5. Seed Team A: 1 Manager, 2 Employees
        User managerA = new User();
        managerA.setEmail("manager.a@techflow.com");
        managerA.setPassword(commonPassword);
        managerA.setRole(Role.MANAGER);
        managerA.setCompany(company);
        managerA.setTeam(teamA);
        managerA.setEnabled(true);
        userRepository.save(managerA);

        User dev1 = new User();
        dev1.setEmail("dev1@techflow.com");
        dev1.setPassword(commonPassword);
        dev1.setRole(Role.EMPLOYEE);
        dev1.setCompany(company);
        dev1.setTeam(teamA);
        dev1.setEnabled(true);
        userRepository.save(dev1);

        User dev2 = new User();
        dev2.setEmail("dev2@techflow.com");
        dev2.setPassword(commonPassword);
        dev2.setRole(Role.EMPLOYEE);
        dev2.setCompany(company);
        dev2.setTeam(teamA);
        dev2.setEnabled(true);
        userRepository.save(dev2);

        // 6. Seed Team B: 1 Manager, 2 Employees
        User managerB = new User();
        managerB.setEmail("manager.b@techflow.com");
        managerB.setPassword(commonPassword);
        managerB.setRole(Role.MANAGER);
        managerB.setCompany(company);
        managerB.setTeam(teamB);
        managerB.setEnabled(true);
        userRepository.save(managerB);

        User designer1 = new User();
        designer1.setEmail("designer1@techflow.com");
        designer1.setPassword(commonPassword);
        designer1.setRole(Role.EMPLOYEE);
        designer1.setCompany(company);
        designer1.setTeam(teamB);
        designer1.setEnabled(true);
        userRepository.save(designer1);

        User designer2 = new User();
        designer2.setEmail("designer2@techflow.com");
        designer2.setPassword(commonPassword);
        designer2.setRole(Role.EMPLOYEE);
        designer2.setCompany(company);
        designer2.setTeam(teamB);
        designer2.setEnabled(true);
        userRepository.save(designer2);

        // 7. Seed an HR User
        User hrUser = new User();
        hrUser.setEmail("hr@techflow.com");
        hrUser.setPassword(commonPassword);
        hrUser.setRole(Role.HR);
        hrUser.setCompany(company);
        hrUser.setEnabled(true);
        userRepository.save(hrUser);

        System.out.println(">>> Database seeded with 2 Teams, 2 Managers, 4 Employees, and 1 HR user.");
        System.out.println(">>> All users password: password123");
    };
}
	}

