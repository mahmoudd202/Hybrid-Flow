package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Team;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    // Find all teams in a specific office
    List<Team> findByOfficeId(Long officeId);

    List<Team> findByManagerId(Long userId);

//    Optional<Team> findByTeamId(Long finalTeamId);
    Optional<Team> findByNameAndCompanyId(String name, Long companyId);

    List<Team> findByCompanyId(Long companyId);

    List<Team> findByCompanyIdOrderByNameAsc(Long companyId);

    List<Team> findByIdInAndCompanyId(List<Long> ids, Long companyId);
}