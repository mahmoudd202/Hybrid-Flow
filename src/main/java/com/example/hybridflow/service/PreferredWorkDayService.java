package com.example.hybridflow.service;

import com.example.hybridflow.dto.PreferredWorkDaysRequestDTO;
import com.example.hybridflow.dto.PreferredWorkDaysResponseDTO;
import com.example.hybridflow.dto.PreferenceCsvRowDto;
import com.example.hybridflow.dto.PreferenceCsvUploadResponse;
import com.example.hybridflow.entity.PreferredWorkDay;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.PreferredWorkDayRepository;
import com.example.hybridflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.InputStreamReader;
import java.io.Reader;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreferredWorkDayService {

    private final PreferredWorkDayRepository preferredWorkDayRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public PreferenceCsvUploadResponse uploadPreferencesCsv(MultipartFile file, User hrUser) {
        Company hrCompany = hrUser.getCompany();
        if (hrCompany == null) {
            return new PreferenceCsvUploadResponse(false, "HR user has no associated company.", 0, 0, 0, 0, null);
        }

        if (file.isEmpty()) {
            return new PreferenceCsvUploadResponse(false, "Uploaded file is empty.", 0, 0, 0, 0, null);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return new PreferenceCsvUploadResponse(false, "Only .csv files are accepted.", 0, 0, 0, 0, null);
        }

        if (file.getSize() > 5 * 1024 * 1024) { // 5 MB
            return new PreferenceCsvUploadResponse(false, "File too large. Maximum allowed size is 5 MB.", 0, 0, 0, 0,
                    null);
        }

        // Each entry: [0] = String[] csvLine, stored as-is; line numbers tracked
        // separately
        List<String[]> csvRows = new ArrayList<>();
        List<Integer> csvLineNumbers = new ArrayList<>(); // actual 1-based line number in the file
        Set<String> emailsToFetch = new HashSet<>();

        // 1. Read all rows from the CSV file
        try (Reader reader = new InputStreamReader(file.getInputStream());
                CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            String[] line;
            int lineNumber = 0;
            boolean headerSkipped = false;
            while ((line = csvReader.readNext()) != null) {
                lineNumber++;
                if (!headerSkipped && lineNumber == 1 && isHeaderRow(line)) {
                    headerSkipped = true;
                    continue;
                }
                if (line.length == 0 || (line.length == 1 && line[0].trim().isEmpty()))
                    continue;

                csvRows.add(line);
                csvLineNumbers.add(lineNumber); // preserve real file line number
                if (line.length > 0) {
                    emailsToFetch.add(line[0].trim());
                }
            }
        } catch (Exception e) {
            return new PreferenceCsvUploadResponse(false, "Parsing failed: " + e.getMessage(), 0, 0, 0, 0, null);
        }

        // 2. Batch fetch users and their companies in exactly one database query (O(1)
        // complexity)
        Map<String, User> userMap = userRepository.findByEmailInWithCompany(emailsToFetch).stream()
                .collect(Collectors.toMap(User::getEmail, u -> u));

        List<PreferenceCsvRowDto> rows = new ArrayList<>();
        List<PreferredWorkDay> preferencesToSave = new ArrayList<>();
        Set<Long> userIdsToClear = new HashSet<>();
        int validCount = 0, invalidCount = 0, savedCount = 0;

        // 3. Process each row in memory against the pre-fetched user map
        for (int i = 0; i < csvRows.size(); i++) {
            String[] line = csvRows.get(i);
            PreferenceCsvRowDto rowDto = new PreferenceCsvRowDto();
            rowDto.setRowNumber(csvLineNumbers.get(i)); // real CSV file line number
            rows.add(rowDto);

            if (line.length < 2) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("Required columns: email, preferred_days (comma separated)");
                invalidCount++;
                continue;
            }

            String email = line[0].trim();
            rowDto.setEmail(email);

            // Collect day values from ALL columns starting at index 1.
            // This handles both CSV formats:
            // Unquoted: dev1@techflow.com,MONDAY,TUESDAY → line[1]="MONDAY",
            // line[2]="TUESDAY"
            // Quoted: dev1@techflow.com,"MONDAY,TUESDAY" → line[1]="MONDAY,TUESDAY"
            List<String> dayList = new ArrayList<>();
            for (int col = 1; col < line.length; col++) {
                Arrays.stream(line[col].split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(dayList::add);
            }
            rowDto.setPreferredDays(dayList);

            // Reject duplicates explicitly rather than silently deduplicating
            long distinctCount = dayList.stream().distinct().count();
            if (distinctCount != dayList.size()) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("Duplicate preferred days are not allowed");
                invalidCount++;
                continue;
            }

            User user = userMap.get(email);
            if (user == null) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("User not found");
                invalidCount++;
                continue;
            }

            if (user.getCompany() == null || !user.getCompany().getId().equals(hrCompany.getId())) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("User does not belong to your company");
                invalidCount++;
                continue;
            }

            boolean limited = false;
            if (dayList.size() > 2) {
                dayList = new ArrayList<>(dayList.subList(0, 2));
                limited = true;
            }

            try {
                List<DayOfWeek> days = dayList.stream()
                        .map(d -> DayOfWeek.valueOf(d.toUpperCase()))
                        .collect(Collectors.toList());

                // Register user ID for bulk deletion and prepare entities for bulk save
                userIdsToClear.add(user.getId());
                for (DayOfWeek day : days) {
                    preferencesToSave.add(PreferredWorkDay.builder()
                            .user(user)
                            .dayOfWeek(day)
                            .build());
                }

                rowDto.setValid(true);
                rowDto.setSaved(true);
                if (limited) {
                    rowDto.setErrorMessage("Saved first 2 chosen days because of the limit of 2 days");
                    rowDto.setPreferredDays(dayList);
                }
                validCount++;
                savedCount++;
            } catch (IllegalArgumentException e) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("Invalid day name(s)");
                invalidCount++;
            }
        }

        // 4. Perform single transaction batch database modifications (delete and save)
        if (!userIdsToClear.isEmpty()) {
            try {
                preferredWorkDayRepository.deleteByUserIdIn(userIdsToClear);
                if (!preferencesToSave.isEmpty()) {
                    preferredWorkDayRepository.saveAll(preferencesToSave);
                }
            } catch (Exception e) {
                savedCount = 0; // reset: nothing was actually persisted
                for (PreferenceCsvRowDto rowDto : rows) {
                    if (rowDto.isValid()) {
                        rowDto.setSaved(false);
                        rowDto.setErrorMessage("Save error: " + e.getMessage());
                    }
                }
                return new PreferenceCsvUploadResponse(false, "Batch save failed: " + e.getMessage(),
                        rows.size(), validCount, invalidCount, savedCount, rows);
            }
        }

        String message = String.format("Parsed %d rows: %d valid, %d invalid, %d saved.",
                rows.size(), validCount, invalidCount, savedCount);
        return new PreferenceCsvUploadResponse(true, message, rows.size(), validCount, invalidCount, savedCount, rows);
    }

    private boolean isHeaderRow(String[] row) {
        if (row.length < 1)
            return false;
        String first = row[0].trim().toLowerCase();
        return first.equals("email") || first.equals("e-mail") || first.equals("mail");
    }
}
