package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.AuthProvider;
import com.example.hybridflow.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByProviderAndProviderId(
            AuthProvider provider,
            String providerId
    );

    // Find all employees in a team
//    List<User> findByTeamId(Long teamId);

    List<User> findAllByTeamId(Long teamId);

    boolean existsByEmail(String email);


}
