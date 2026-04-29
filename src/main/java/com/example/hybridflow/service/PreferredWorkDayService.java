package com.example.hybridflow.service;

import com.example.hybridflow.dto.PreferredWorkDaysRequestDTO;
import com.example.hybridflow.dto.PreferredWorkDaysResponseDTO;
import com.example.hybridflow.entity.PreferredWorkDay;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.PreferredWorkDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreferredWorkDayService {

    private final PreferredWorkDayRepository preferredWorkDayRepository;

    @Transactional
    public PreferredWorkDaysResponseDTO setPreferredDays(PreferredWorkDaysRequestDTO dto, User user) {
        if (dto.getPreferredDays() == null) {
            throw new BusinessValidationException("Preferred days list cannot be null");
        }

        if (dto.getPreferredDays().size() > 2) {
            throw new BusinessValidationException("You can select a maximum of 2 preferred online days");
        }

        // Remove existing preferences to overwrite
        preferredWorkDayRepository.deleteByUserId(user.getId());

        // Save new preferences
        List<PreferredWorkDay> preferences = dto.getPreferredDays().stream()
                .map(day -> PreferredWorkDay.builder()
                        .user(user)
                        .dayOfWeek(day)
                        .build())
                .collect(Collectors.toList());

        preferredWorkDayRepository.saveAll(preferences);

        return buildResponse(user, dto.getPreferredDays());
    }

    public PreferredWorkDaysResponseDTO getMyPreferredDays(User user) {
        Set<DayOfWeek> days = preferredWorkDayRepository.findByUserId(user.getId()).stream()
                .map(PreferredWorkDay::getDayOfWeek)
                .collect(Collectors.toSet());

        return buildResponse(user, days);
    }

    private PreferredWorkDaysResponseDTO buildResponse(User user, Set<DayOfWeek> days) {
        return PreferredWorkDaysResponseDTO.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .preferredDays(days)
                .build();
    }
}