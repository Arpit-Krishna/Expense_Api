package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.SignupRequest;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.util.JwTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServicies implements UserDetailsService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    JwTUtil jwtUtil;
    @Autowired
    PasswordEncoder passwordEncoder;

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
        userRepository.save(user);
        return jwtUtil.generateToken(username);
    }

    public String login(String username, String password) {
        if (username == null || password == null) {
            throw ApiException.unauthorized("Wrong username or password");
        }
        User user = userRepository.findFirstByUsername(username.trim())
                .orElseThrow(() -> ApiException.unauthorized("Wrong username or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw ApiException.unauthorized("Wrong username or password");
        }
        return jwtUtil.generateToken(user.getUsername());
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
