package com.expenses.Expense_Api.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[A-Za-z0-9 ._-]{3,30}$", message = "Username must be 3-30 letters, numbers, spaces, dots, dashes or underscores")
        String username,
        @Size(max = 60, message = "Name must be at most 60 characters")
        String fullName,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password,
        @Email(message = "Please enter a valid email address")
        String email,
        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone
) {}
