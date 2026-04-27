package com.example.hybridflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Request;
import com.example.hybridflow.entity.RequestStatus;

import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByRequesterId(Long userId);

    List<Request> findByCompanyId(Long companyId);
    void deleteByCompanyId(Long companyId);

    // Added pagination because this list can get very long
    Page<Request> findByCompanyId(Long companyId, Pageable pageable);
    // check the class below to get to know how to use page/pageable  or just use this:
//    List<Request> findByCompanyId(Long companyId);

    List<Request> findByCompanyIdAndStatus(Long companyId, RequestStatus status);

    // For duplicate/overlap detection
    List<Request> findByRequesterIdAndStatusAndType(Long requesterId, RequestStatus status, com.example.hybridflow.entity.RequestType type);
}


//@GetMapping("/requests")
//public Page<Request> getRequests(
//        @RequestParam(defaultValue = "0") int page,
//        @RequestParam(defaultValue = "10") int size) {
//
//    Pageable pageable = PageRequest.of(page, size);
//    return requestRepository.findByCompanyId(myCompanyId, pageable);
//}