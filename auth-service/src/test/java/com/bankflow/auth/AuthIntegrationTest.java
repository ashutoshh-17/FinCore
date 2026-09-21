package com.bankflow.auth;

import com.bankflow.auth.modules.auth.dto.LoginRequest;
import com.bankflow.auth.modules.auth.dto.RegisterRequest;
import com.bankflow.auth.modules.auth.dto.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: full Spring context + real PostgreSQL via Testcontainers.
 *
 * <p>Verifies the complete register → login → profile flow end-to-end, including
 * Flyway migrations, password hashing and JWT issuance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_db")
            .withUsername("bankflow")
            .withPassword("bankflow_secret");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Kafka for this test — outbox relay will fail gracefully
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("jwt.secret", () -> "test-secret-that-is-long-enough-for-hmac-sha256-minimum-256-bits");
        registry.add("jwt.access-token-expiry-ms", () -> "900000");
        registry.add("jwt.refresh-token-expiry-days", () -> "7");
        registry.add("internal.service-secret", () -> "test-secret");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void register_thenLogin_thenGetProfile_fullFlow() throws Exception {
        // 1. Register
        RegisterRequest registerRequest = new RegisterRequest(
                "integration@example.com", "StrongPass1!", "Integration User");

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse registerResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);

        assertThat(registerResponse.email()).isEqualTo("integration@example.com");
        assertThat(registerResponse.accessToken()).isNotBlank();

        // 2. Login with the same credentials
        LoginRequest loginRequest = new LoginRequest("integration@example.com", "StrongPass1!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        AuthResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        // 3. Get profile using the access token
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("integration@example.com"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate@example.com", "StrongPass1!", "Dupe User");

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        RegisterRequest reg = new RegisterRequest("wrongpass@example.com", "CorrectPass1!", "WP User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("wrongpass@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accessProtectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
