package com.bankflow.auth.modules.auth;

import com.bankflow.auth.modules.token.RefreshToken;
import com.bankflow.auth.modules.token.RefreshTokenRepository;
import com.bankflow.auth.modules.user.*;
import com.bankflow.auth.outbox.OutboxEventRepository;
import com.bankflow.auth.security.JwtService;
import com.bankflow.auth.modules.auth.dto.LoginRequest;
import com.bankflow.auth.modules.auth.dto.RegisterRequest;
import com.bankflow.auth.modules.auth.dto.AuthResponse;
import com.bankflow.common.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Naming convention: methodName_condition_expectedResult
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository         userRepository;
    @Mock private RoleRepository         roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OutboxEventRepository  outboxEventRepository;
    @Mock private JwtService             jwtService;
    @Mock private PasswordEncoder        passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private Role customerRole;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenExpiryMs",  900_000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7L);
        ReflectionTestUtils.setField(authService, "objectMapper", new ObjectMapper());

        customerRole = new Role();
        ReflectionTestUtils.setField(customerRole, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(customerRole, "name", "CUSTOMER");
    }

    // ── register ──────────────────────────────────────────────────

    @Test
    void register_newEmail_returnsAuthResponseWithTokens() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "Password1!", "Alice");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.email()).isEqualTo("alice@example.com");

        verify(outboxEventRepository).save(argThat(e ->
                "UserRegistered".equals(e.getEventType())));
    }

    @Test
    void register_duplicateEmail_throwsAuthExceptionWithCorrectCode() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("alice@example.com", "Password1!", "Alice");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCode())
                        .isEqualTo(ErrorCodes.EMAIL_ALREADY_REGISTERED));

        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsTokens() {
        User user = User.create("bob@example.com", "hashed", "Bob", customerRole);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        when(userRepository.findByEmailWithRoles("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");

        AuthResponse response = authService.login(new LoginRequest("bob@example.com", "Password1!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmailWithRoles(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "pw")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCode())
                        .isEqualTo(ErrorCodes.INVALID_CREDENTIALS));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = User.create("bob@example.com", "hashed", "Bob", customerRole);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        when(userRepository.findByEmailWithRoles("bob@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("bob@example.com", "wrong")))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getCode())
                        .isEqualTo(ErrorCodes.INVALID_CREDENTIALS));
    }

    // ── logout ────────────────────────────────────────────────────

    @Test
    void logout_validUser_revokesAllTokens() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.revokeAllByUserId(eq(userId), any())).thenReturn(2);

        authService.logout(userId);

        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any());
    }
}
