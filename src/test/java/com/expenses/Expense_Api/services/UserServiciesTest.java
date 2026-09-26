package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.SignupRequest;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.security.LoginAttemptLimiter;
import com.expenses.Expense_Api.util.JwTUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiciesTest {

    private UserServicies service;
    private UserRepository repo;

    @BeforeEach
    void setUp() {
        service = new UserServicies();
        repo = mock(UserRepository.class);
        service.userRepository = repo;
        service.jwtUtil = new JwTUtil("a-test-secret-that-is-long-enough-for-hs256", 1);
        service.passwordEncoder = new BCryptPasswordEncoder(4);
        service.loginAttemptLimiter = new LoginAttemptLimiter(3);

        User arpit = new User();
        arpit.setUsername("arpit");
        arpit.setPassword(service.passwordEncoder.encode("correct-horse"));
        when(repo.findFirstByUsername("arpit")).thenReturn(Optional.of(arpit));
        when(repo.findFirstByUsername("ghost")).thenReturn(Optional.empty());
    }

    private static HttpStatus statusOf(Runnable r) {
        try {
            r.run();
            return HttpStatus.OK;
        } catch (ApiException e) {
            return e.getStatus();
        }
    }

    @Test
    void correctPasswordReturnsToken() {
        String token = service.login(" arpit ", "correct-horse", "1.1.1.1");

        assertThat(service.jwtUtil.extractUsername(token)).isEqualTo("arpit");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() {
        assertThatThrownBy(() -> service.login("arpit", "nope", "1.1.1.1"))
                .isInstanceOf(ApiException.class).hasMessage("Wrong username or password");
        assertThatThrownBy(() -> service.login("ghost", "nope", "1.1.1.1"))
                .isInstanceOf(ApiException.class).hasMessage("Wrong username or password");
        assertThatThrownBy(() -> service.login("", "", "1.1.1.1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.login(null, null, "1.1.1.1")).isInstanceOf(ApiException.class);
    }

    @Test
    void repeatedFailuresLockOutEvenTheRightPassword() {
        for (int i = 0; i < 3; i++) {
            assertThat(statusOf(() -> service.login("arpit", "guess", "6.6.6.6"))).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(statusOf(() -> service.login("arpit", "correct-horse", "6.6.6.6"))).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // The real owner on another connection can still get in.
        assertThat(statusOf(() -> service.login("arpit", "correct-horse", "1.1.1.1"))).isEqualTo(HttpStatus.OK);
    }

    @Test
    void signupConflictsOnTakenUsernameAndOnRace() {
        when(repo.existsByUsername("arpit")).thenReturn(true);
        assertThat(statusOf(() -> service.createUser(new SignupRequest("arpit", null, "password123", null, null))))
                .isEqualTo(HttpStatus.CONFLICT);

        when(repo.existsByUsername("new-user")).thenReturn(false);
        when(repo.save(any())).thenThrow(new DuplicateKeyException("E11000"));
        assertThat(statusOf(() -> service.createUser(new SignupRequest("new-user", null, "password123", null, null))))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void signupStoresAHashNotThePassword() {
        when(repo.existsByUsername("new-user")).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertThat(saved.getPassword()).isNotEqualTo("password123").startsWith("$2");
            return saved;
        });

        String token = service.createUser(new SignupRequest(" new-user ", " New User ", "password123", null, null));

        assertThat(service.jwtUtil.extractUsername(token)).isEqualTo("new-user");
    }
}
