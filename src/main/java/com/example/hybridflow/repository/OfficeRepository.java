package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.Office;

public interface OfficeRepository extends JpaRepository<Office, Long> {
}
