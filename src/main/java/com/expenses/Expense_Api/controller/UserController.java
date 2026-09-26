package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.LoginRequest;
import com.expenses.Expense_Api.DTO.SignupRequest;
import com.expenses.Expense_Api.DTO.UserResponceDTO;
import com.expenses.Expense_Api.services.UserServicies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class UserController {

    @Autowired
    UserServicies userServicies;

    /** Lets uptime monitors (and the frontend's wake-up ping) hit the API without logging in. */
    @GetMapping({"/", "/health"})
    public Map<String, String> health() {
        return Map.of("status", "ok");
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
