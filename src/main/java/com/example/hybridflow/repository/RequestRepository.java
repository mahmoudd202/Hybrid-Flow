package com.example.hybridflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Request;
import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.entity.RequestType;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByRequesterId(Long userId);

    List<Request> findByCompanyId(Long companyId);

    void deleteByCompanyId(Long companyId);

    Page<Request> findByCompanyId(Long companyId, Pageable pageable);

    List<Request> findByCompanyIdAndStatus(Long companyId, RequestStatus status);

    List<Request> findByRequesterIdAndStatusAndType(Long requesterId, RequestStatus status, RequestType type);

    @Query("""
                select r from Request r
                join fetch r.requester req
                left join fetch r.handledBy hb
                where r.company.id = :companyId
                  and (:status is null or r.status = :status)
                  and (:type is null or r.type = :type)
                  and (:requesterId is null or req.id = :requesterId)
                  and (:startDate is null or r.startDate >= :startDate)
                  and (:endDate is null or r.endDate <= :endDate)
                order by r.createdAt desc
            """)
    List<Request> findCompanyRequestHistoryWithFilters(
            @Param("companyId") Long companyId,
            @Param("status") RequestStatus status,
            @Param("type") RequestType type,
            @Param("requesterId") Long requesterId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
