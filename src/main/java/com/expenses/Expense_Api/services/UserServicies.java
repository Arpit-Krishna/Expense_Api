package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.util.JwTUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServicies implements UserDetailsService {

    @Autowired
    UserRepository userRepository;
    @Autowired
    JwTUtil jwtUtil;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.expenses.Expense_Api.model.User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }

    public String createUser(User user){
        user.setPassword(new BCryptPasswordEncoder().encode(user.getPassword()));
        userRepository.save(user);
        return jwtUtil.generateToken(user.getUsername());
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (new BCryptPasswordEncoder().matches(password, user.getPassword())) {
            return jwtUtil.generateToken(username);
        } else {
            throw new RuntimeException("Invalid credentials");
        }
    }

    public Optional<User> getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        return userRepository.findByUsername(username);
    }

}
