package com.expenses.Expense_Api.controller;


import com.expenses.Expense_Api.DTO.LoginRequest;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.services.UserServicies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class UserController {

    @Autowired
    UserServicies userServicies;

    @PostMapping("/signup")
    public String createUser(@RequestBody User user){
        return userServicies.createUser(user);
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest user){
        return userServicies.login(user.getUsername(), user.getPassword());
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(HttpServletRequest request){
        Optional<User> user = userServicies.getCurrentUser(request);

        return user.map(ResponseEntity::ok).orElseGet(() ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }


}
