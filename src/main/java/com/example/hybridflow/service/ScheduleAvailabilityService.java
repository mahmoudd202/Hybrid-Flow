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
                        "User " + user.getEmail() + " has no published schedule entry on " + date));

        if (entry.getWorkMode() == WorkMode.OFF) {
            throw new BusinessValidationException(
                    "User " + user.getEmail() + " is OFF on " + date);
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

            if (entry.getWorkMode() == WorkMode.OFF) {
                conflicts.add(user.getEmail() + " is OFF on " + date);
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join("; ", conflicts));
        }
    }

    public List<String> findUnavailableUserEmailsOnDate(List<User> users, LocalDate date) {
        List<String> unavailableUsers = new ArrayList<>();

        for (User user : users) {
            ScheduleEntry entry = scheduleEntryRepository.findPublishedEntryForUserOnDate(user.getId(), date)
                    .orElse(null);

            if (entry == null) {
                unavailableUsers.add(user.getEmail() + " has no published schedule entry on " + date);
                continue;
            }

            if (entry.getWorkMode() == WorkMode.OFF) {
                unavailableUsers.add(user.getEmail() + " is OFF on " + date);
            }
        }

        return unavailableUsers;
    }
}