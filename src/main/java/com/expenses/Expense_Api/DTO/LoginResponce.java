package com.expenses.Expense_Api.DTO;

public class LoginResponce {
    private String token;

    public LoginResponce(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
