package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.LoginRequest;
import com.expenses.Expense_Api.DTO.SignupRequest;
import com.expenses.Expense_Api.DTO.UserResponceDTO;
import com.expenses.Expense_Api.services.DatabaseHealth;
import com.expenses.Expense_Api.services.UserServicies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class UserController {

    @Autowired
    UserServicies userServicies;
    @Autowired
    DatabaseHealth databaseHealth;

    /**
     * Lets uptime monitors (and the frontend's wake-up ping) hit the API without logging in.
     * With ?db=true it also pings MongoDB, which keeps the free Atlas cluster from pausing.
     */
    @GetMapping({"/", "/health"})
    public ResponseEntity<Map<String, String>> health(@RequestParam(defaultValue = "false") boolean db) {
        if (!db) return ResponseEntity.ok(Map.of("status", "ok"));
        if (databaseHealth.isUp()) return ResponseEntity.ok(Map.of("status", "ok", "db", "ok"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "degraded", "db", "down"));
    }

    @PostMapping("/auth/signup")
    public String createUser(@Valid @RequestBody SignupRequest request) {
        return userServicies.createUser(request);
    }

    @PostMapping("/auth/login")
    public String login(@RequestBody LoginRequest user, HttpServletRequest request) {
        return userServicies.login(user.getUsername(), user.getPassword(), request.getRemoteAddr());
    }

    @GetMapping("/auth/me")
    public UserResponceDTO getCurrentUser() {
        return UserResponceDTO.from(userServicies.currentUser());
    }
}
