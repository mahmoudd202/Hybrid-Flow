package com.example.hybridflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.hybridflow.repository.PreferredWorkDayRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.PreferredWorkDay;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class PreferenceCsvUploadTest {

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired UserRepository userRepository;
    @Autowired PreferredWorkDayRepository preferredWorkDayRepository;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Login as HR
        String body = objectMapper.writeValueAsString(
                Map.of("email", "hr@techflow.com", "password", "password123"));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        hrToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void uploadCsvWithMoreThanTwoDaysSavesFirstTwoAndReturnsMessage() throws Exception {
        String csvContent = "email,preferred_days\n" +
                "dev1@techflow.com,MONDAY,TUESDAY,WEDNESDAY\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "preferences.csv",
                "text/csv",
                csvContent.getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/preferences/upload-csv")
                        .file(file)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("success").asBoolean()).isTrue();
        assertThat(response.get("totalRows").asInt()).isEqualTo(1);
        assertThat(response.get("validRows").asInt()).isEqualTo(1);
        assertThat(response.get("savedRows").asInt()).isEqualTo(1);

        JsonNode rowData = response.get("data").get(0);
        assertThat(rowData.get("email").asText()).isEqualTo("dev1@techflow.com");
        assertThat(rowData.get("valid").asBoolean()).isTrue();
        assertThat(rowData.get("saved").asBoolean()).isTrue();
        assertThat(rowData.get("errorMessage").asText())
                .isEqualTo("Saved first 2 chosen days because of the limit of 2 days");

        // Verify elements inside the preferredDays JSON list are just MONDAY and TUESDAY
        JsonNode preferredDaysNode = rowData.get("preferredDays");
        assertThat(preferredDaysNode.size()).isEqualTo(2);
        assertThat(preferredDaysNode.get(0).asText()).isEqualTo("MONDAY");
        assertThat(preferredDaysNode.get(1).asText()).isEqualTo("TUESDAY");

        // Verify DB contains only MONDAY and TUESDAY for dev1
        User dev1 = userRepository.findByEmail("dev1@techflow.com").orElseThrow();
        List<DayOfWeek> savedDays = preferredWorkDayRepository.findByUserId(dev1.getId()).stream()
                .map(PreferredWorkDay::getDayOfWeek)
                .collect(Collectors.toList());

        assertThat(savedDays).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    }
}
