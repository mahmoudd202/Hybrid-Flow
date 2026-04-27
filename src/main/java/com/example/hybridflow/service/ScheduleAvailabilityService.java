package com.example.hybridflow.service;

import org.springframework.stereotype.Service;

import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.ScheduleEntryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduleAvailabilityService {

    private final ScheduleEntryRepository scheduleEntryRepository;

    public ScheduleAvailabilityService(ScheduleEntryRepository scheduleEntryRepository) {
        this.scheduleEntryRepository = scheduleEntryRepository;
    }

    public void validateUserIsSchedulableOnDate(User user, LocalDate date) {
        ScheduleEntry entry = scheduleEntryRepository.findPublishedEntryForUserOnDate(user.getId(), date)
                .orElseThrow(() -> new BusinessValidationException(
                        "User " + user.getEmail() + " has no published schedule entry on " + date
                ));

        if (entry.getWorkMode() == WorkMode.OFF || entry.getWorkMode() == WorkMode.SICK_LEAVE) {
            throw new BusinessValidationException(
                    "User " + user.getEmail() + " is " + entry.getWorkMode().name() + " on " + date
            );
        }
    }

    public void validateUsersAreSchedulableOnDate(List<User> users, LocalDate date) {
        List<String> conflicts = new ArrayList<>();

        for (User user : users) {
            ScheduleEntry entry = scheduleEntryRepository.findPublishedEntryForUserOnDate(user.getId(), date)
                    .orElse(null);

            if (entry == null) {
                conflicts.add(user.getEmail() + " has no published schedule entry on " + date);
                continue;
            }

            if (entry.getWorkMode() == WorkMode.OFF || entry.getWorkMode() == WorkMode.SICK_LEAVE) {
                conflicts.add(user.getEmail() + " is " + entry.getWorkMode().name() + " on " + date);
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join("; ", conflicts));
        }
    }
}