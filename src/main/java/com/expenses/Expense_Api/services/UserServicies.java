package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.SignupRequest;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.security.LoginAttemptLimiter;
import com.expenses.Expense_Api.util.JwTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServicies implements UserDetailsService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    JwTUtil jwtUtil;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    LoginAttemptLimiter loginAttemptLimiter;

    /** Compared against when the username does not exist, so both failures take the same time. */
    private volatile String dummyHash;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findFirstByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }

    public String createUser(SignupRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("That username is already taken");
        }
        User user = new User();
        user.setUsername(username);
        user.setFullName(request.fullName() == null ? null : request.fullName().trim());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            // Two signups raced past the check above; the unique index caught the second one.
            throw ApiException.conflict("That username is already taken");
        }
        return jwtUtil.generateToken(username);
    }

    public String login(String username, String password, String clientIp) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.unauthorized("Wrong username or password");
        }
        username = username.trim();
        long wait = loginAttemptLimiter.secondsUntilAllowed(username, clientIp);
        if (wait > 0) {
            throw ApiException.tooManyRequests(
                    "Too many failed attempts. Try again in " + Math.max(1, (wait + 59) / 60) + " minute(s).");
        }

        Optional<User> user = userRepository.findFirstByUsername(username);
        String hash = user.map(User::getPassword).orElseGet(this::dummyHash);
        boolean matches = hash != null && passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !matches) {
            loginAttemptLimiter.recordFailure(username, clientIp);
            throw ApiException.unauthorized("Wrong username or password");
        }
        loginAttemptLimiter.recordSuccess(username, clientIp);
        return jwtUtil.generateToken(user.get().getUsername());
    }

    private String dummyHash() {
        if (dummyHash == null) dummyHash = passwordEncoder.encode("not-a-real-password");
        return dummyHash;
    }

    /** The logged-in user, taken from the security context the JWT filter set. */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails details)) {
            throw ApiException.unauthorized("Please log in again");
        }
        return userRepository.findFirstByUsername(details.getUsername())
                .orElseThrow(() -> ApiException.unauthorized("Please log in again"));
    }
}
