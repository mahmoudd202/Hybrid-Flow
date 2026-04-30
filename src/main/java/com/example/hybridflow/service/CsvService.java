package com.example.hybridflow.service;

import com.example.hybridflow.dto.CsvRowDto;
import com.example.hybridflow.dto.CsvUploadResponse;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class CsvService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Maximum allowed file size for CSV uploads (5 MB)
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public CsvService(TeamRepository teamRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CsvUploadResponse parseCsvFile(MultipartFile file, User hrUser) {

        Company hrCompany = hrUser.getCompany();

        if (hrCompany == null) {
            return new CsvUploadResponse(false, "HR user has no associated company.", 0, 0, 0, 0, null);
        }

        // Basic file validation
        if (file.isEmpty()) {
            return new CsvUploadResponse(false, "Uploaded file is empty.", 0, 0, 0, 0, null);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return new CsvUploadResponse(false, "Only .csv files are accepted.", 0, 0, 0, 0, null);
        }

        // File size check
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return new CsvUploadResponse(false,
                    "File too large. Maximum allowed size is 5 MB.", 0, 0, 0, 0, null);
        }

        List<CsvRowDto> rows = new ArrayList<>();
        int validCount = 0, invalidCount = 0, savedCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream());
                CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            // Read the first line to check for a header
            String[] firstLine = csvReader.readNext();
            if (firstLine == null) {
                return new CsvUploadResponse(false, "CSV file contains no data.", 0, 0, 0, 0, null);
            }

            boolean hasHeader = isHeaderRow(firstLine);
            int rowNumber = 1;

            // Cache by team name (lowercased) — scoped to this company already via the DB lookup
            Map<String, Team> teamCache = new HashMap<>();

            // If the first line is NOT a header, process it as data
            if (!hasHeader) {
                CsvRowDto rowDto = processLine(firstLine, rowNumber, hrCompany, teamCache);
                rows.add(rowDto);
                if (rowDto.isValid()) {
                    validCount++;
                    if (rowDto.isSaved())
                        savedCount++;
                } else {
                    invalidCount++;
                }
            }

            // Stream remaining lines one at a time (memory-efficient)
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                rowNumber++;

                // Skip blank lines
                if (line.length == 0 || (line.length == 1 && line[0].trim().isEmpty()))
                    continue;

                CsvRowDto rowDto = processLine(line, rowNumber, hrCompany, teamCache);
                rows.add(rowDto);
                if (rowDto.isValid()) {
                    validCount++;
                    if (rowDto.isSaved())
                        savedCount++;
                } else {
                    invalidCount++;
                }
            }

            String message = String.format(
                    "Parsed %d rows: %d valid, %d invalid, %d saved.",
                    rows.size(), validCount, invalidCount, savedCount);
            return new CsvUploadResponse(true, message, rows.size(), validCount, invalidCount, savedCount, rows);

        } catch (Exception e) {
            return new CsvUploadResponse(false, "Parsing failed: " + e.getMessage(), 0, 0, 0, 0, null);
        }
    }

    private CsvRowDto processLine(String[] line, int rowNumber,
            Company hrCompany, Map<String, Team> teamCache) {
        CsvRowDto rowDto = new CsvRowDto();
        rowDto.setRowNumber(rowNumber);

        if (line.length < 3) {
            rowDto.setValid(false);
            rowDto.setErrorMessage("Required columns: email, role, team_name");
            return rowDto;
        }

        processRow(line, rowDto, hrCompany, teamCache);
        return rowDto;
    }

    private void processRow(String[] line,
            CsvRowDto rowDto,
            Company hrCompany,
            Map<String, Team> teamCache) {

        String email = line[0].trim();
        String roleStr = line[1].trim();
        String teamName = line[2].trim();

        rowDto.setEmail(email);
        rowDto.setRole(roleStr);
        rowDto.setTeamName(teamName);

        List<String> errors = new ArrayList<>();

        // Validate email
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            errors.add("Invalid email format");
        } else if (userRepository.existsByEmail(email)) {
            errors.add("Email already registered");
        }

        // Validate role
        Role role = null;
        try {
            role = Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid role. Accepted: EMPLOYEE, MANAGER, HR");
        }

        // Validate team name
        if (teamName.isEmpty()) {
            errors.add("Team name cannot be empty");
        }

        if (errors.isEmpty()) {
            rowDto.setValid(true);
            try {
                String cacheKey = teamName.toLowerCase();
                final Company company = hrCompany;

                // Reuse existing team in this company, or create a new one 
                Team team = teamCache.computeIfAbsent(cacheKey,
                        k -> teamRepository.findByNameAndCompanyId(teamName, company.getId())
                                .orElseGet(() -> {
                                    Team newTeam = new Team();
                                    newTeam.setName(teamName);
                                    newTeam.setCompany(company); // scoped to HR's company
                                    // office is intentionally null — HR will assign it later
                                    return teamRepository.save(newTeam);
                                }));

                // Create user — always pinned to HR's company
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setRole(role);
                newUser.setTeam(team);
                newUser.setCompany(hrCompany); // same company as the HR who uploaded
                newUser.setEnabled(false); // must go through invite flow to activate
                newUser.setProvider(AuthProvider.LOCAL);

                userRepository.save(newUser);

                // If the user is a MANAGER, handle team assignment strictly
                if (role == Role.MANAGER) {
                    if (team.getManager() == null) {
                        team.setManager(newUser);
                        teamRepository.save(team);
                    } else if (!team.getManager().getId().equals(newUser.getId())) {
                        // If team already has a DIFFERENT manager, we don't overwrite.
                        // We mark the row as valid (user created) but add a warning/error about the team manager
                        rowDto.setErrorMessage("User created, but Team '" + teamName + "' already has a manager. Assignment skipped.");
                    }
                }

                rowDto.setSaved(true);

            } catch (Exception e) {
                rowDto.setValid(false);
                rowDto.setErrorMessage("Save error: " + e.getMessage());
            }
        } else {
            rowDto.setValid(false);
            rowDto.setErrorMessage(String.join("; ", errors));
        }
    }

    private boolean isHeaderRow(String[] row) {
        if (row.length < 1)
            return false;
        String first = row[0].trim().toLowerCase();
        return first.equals("email") || first.equals("e-mail") || first.equals("mail");
    }
}
