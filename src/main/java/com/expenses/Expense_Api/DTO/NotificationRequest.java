package com.expenses.Expense_Api.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @Email(message = "Please enter a valid email address")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email,
        boolean weeklySummary,
        boolean limitAlerts,
        boolean dueReminders
) {}
