package com.expenses.Expense_Api.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecurringRequest(
        @NotBlank(message = "Name cannot be empty")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String title,
        @Size(max = 40, message = "Category must be at most 40 characters")
        String category,
        @Positive(message = "Amount must be greater than zero")
        @Max(value = 1_000_000_000, message = "Amount is too large")
        double amount,
        @Min(value = 1, message = "Day must be between 1 and 31")
        @Max(value = 31, message = "Day must be between 1 and 31")
        int dayOfMonth,
        Boolean active,
        @Size(max = 500, message = "Note must be at most 500 characters")
        String note
) {}
