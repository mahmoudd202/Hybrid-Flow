package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.AuthProvider;
import com.example.hybridflow.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
    select u from User u
    left join fetch u.company
    left join fetch u.team t
    left join fetch t.office
    where u.email = :email
""")
    Optional<User> findAuthContextByEmail(@Param("email") String email);
}