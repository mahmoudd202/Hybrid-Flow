package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.Invitation;
import com.example.hybridflow.entity.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    boolean existsByEmailAndUsedFalse(String email);

    Optional<Invitation> findFirstByEmailAndUsedFalseAndExpiryDateAfter(String email, Instant now);

    List<Invitation> findByCompanyIdAndUsedFalseAndExpiryDateAfter(Long companyId, Instant now);

    List<Invitation> findByTeamIdAndRoleAndUsedFalseAndExpiryDateAfter(Long teamId, Role role, Instant now);
}
