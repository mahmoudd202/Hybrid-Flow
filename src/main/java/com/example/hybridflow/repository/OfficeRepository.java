package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.Office;

import java.util.List;
import java.util.Optional;

public interface OfficeRepository extends JpaRepository<Office, Long> {

    List<Office> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Office> findFirstByCompanyIdOrderByIdAsc(Long companyId);

    boolean existsByNameAndCompanyId(String name, Long companyId);
}