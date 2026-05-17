package com.example.hybridflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 1 — Auth Flow
 *
 * Verifies login, logout, token invalidation, and role-based access rejection
 * for all three seeded user roles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired WebApplicationContext webApplicationContext;

    MockMvc mockMvc;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    // -----------------------------------------------------------------------
    // Successful logins — token must be issued for each seeded role
    // -----------------------------------------------------------------------

    @Test
    void hrLoginReturnsToken() throws Exception {
        String token = loginAndGetToken("hr@techflow.com", "password123");
        assertThat(token).isNotBlank();
    }

    @Test
    void managerLoginReturnsToken() throws Exception {
        String token = loginAndGetToken("manager.a@techflow.com", "password123");
        assertThat(token).isNotBlank();
    }

    @Test
    void employeeLoginReturnsToken() throws Exception {
        String token = loginAndGetToken("dev1@techflow.com", "password123");
        assertThat(token).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // Wrong password → 401
    // -----------------------------------------------------------------------

    @Test
    void wrongPasswordReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("email", "hr@techflow.com", "password", "wrongpassword"));

        // AuthService throws BusinessValidationException("Invalid email or password.")
        // which GlobalExceptionHandler maps to 400 BAD_REQUEST (not 401)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // Logout → 200
    // -----------------------------------------------------------------------

    @Test
    void logoutReturns200() throws Exception {
        String token = loginAndGetToken("manager.a@techflow.com", "password123");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // Invalidated token → 401 on any protected endpoint
    // -----------------------------------------------------------------------

    @Test
    void invalidatedTokenIsRejected() throws Exception {
        String token = loginAndGetToken("dev1@techflow.com", "password123");

        // Logout — token is now blacklisted
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Reuse the same token — must get 401
        mockMvc.perform(get("/api/requests/my-requests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Role-based access — HR token on a MANAGER-only endpoint → 403
    // -----------------------------------------------------------------------

    @Test
    void hrTokenOnManagerEndpointReturns403() throws Exception {
        String token = loginAndGetToken("hr@techflow.com", "password123");

        // POST /api/tasks is @PreAuthorize("hasRole('MANAGER')")
        // A valid body is required: @Valid runs before @PreAuthorize, so {} would
        // fail bean validation (400) before the role check fires.
        String validBody = objectMapper.writeValueAsString(Map.of(
                "title", "Test task",
                "targetType", "INDIVIDUAL",
                "dueDate", "2099-12-31T10:00:00"
        ));

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Role-based access — EMPLOYEE token on an HR-only endpoint → 403
    // -----------------------------------------------------------------------

    @Test
    void employeeTokenOnHrEndpointReturns403() throws Exception {
        String token = loginAndGetToken("dev1@techflow.com", "password123");

        // GET /api/requests/pending is @PreAuthorize("hasRole('HR')")
        mockMvc.perform(get("/api/requests/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
