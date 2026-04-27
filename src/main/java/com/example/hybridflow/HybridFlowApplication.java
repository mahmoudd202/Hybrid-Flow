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
//			
//		};
	}

