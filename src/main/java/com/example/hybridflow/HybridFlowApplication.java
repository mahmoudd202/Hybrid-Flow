package com.example.hybridflow;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDate;

@SpringBootApplication
@EnableScheduling
public class HybridFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(HybridFlowApplication.class, args);
	}

//	@Bean
//	public CommandLineRunner commandLineRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
//		return args -> {
//			// Create a default User
//			User user = new User();
//			user.setUsername("mahmoud");
//			user.setPassword(passwordEncoder.encode("password123"));
//			user.setEmail("mahmoudd202@hotmail.com");
//			user.setNationality("Palestinian");
//			user.setDateOfBirth(LocalDate.of(2005,2,24));
//			user.setRole(Role.USER);
//			userRepository.save(user);
//
//			// Create a default Admin
//			User admin = new User();
//			admin.setUsername("admin");
//			admin.setPassword(passwordEncoder.encode("admin123"));
//			admin.setEmail("Admin@hotmail.com");
//			admin.setNationality("Turkish");
//			admin.setDateOfBirth(LocalDate.of(2025,12,24));
//			admin.setRole(Role.ADMIN);
//			userRepository.save(admin);
//			// 2. Generate a Refresh Token for this Admin
////			RefreshToken rt = refreshTokenService.createRefreshToken(admin);
//
//			System.out.println("--- FAST TEST DATA LOADED ---");
//			System.out.println("User: mahmoud / password123");
//			System.out.println("Admin: admin / admin123");
////			System.out.println("Refresh Token for Postman: " + rt.getToken());
//		};
	}

