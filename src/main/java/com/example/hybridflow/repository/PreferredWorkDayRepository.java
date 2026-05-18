package com.example.hybridflow.repository;

import com.example.hybridflow.entity.PreferredWorkDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PreferredWorkDayRepository extends JpaRepository<PreferredWorkDay, Long> {
    List<PreferredWorkDay> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM PreferredWorkDay p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PreferredWorkDay p WHERE p.user.id IN :userIds")
    void deleteByUserIdIn(@Param("userIds") java.util.Collection<Long> userIds);
}
