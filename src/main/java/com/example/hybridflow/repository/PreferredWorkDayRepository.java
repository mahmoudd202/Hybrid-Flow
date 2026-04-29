package com.example.hybridflow.repository;

import com.example.hybridflow.entity.PreferredWorkDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PreferredWorkDayRepository extends JpaRepository<PreferredWorkDay, Long> {
    List<PreferredWorkDay> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
