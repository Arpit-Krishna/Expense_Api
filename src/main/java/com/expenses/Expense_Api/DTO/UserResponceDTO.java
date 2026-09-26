package com.expenses.Expense_Api.DTO;

import com.expenses.Expense_Api.model.User;

public record UserResponceDTO(
        String id,
        String username,
        String fullName,
        String email,
        String phone
) {
    public static UserResponceDTO from(User user) {
        return new UserResponceDTO(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
