package com.expenses.Expense_Api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "Expence")
public class Expence {
    @Id
    private String id;

    @NotBlank(message = "Title cannot be null or empty")
    private String title;

    @NotBlank(message = "Description cannot be null or empty")
    private String description;

    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than zero")
    private double amount;
    private LocalDateTime date;

    //@DBRef
    //private User user;
    private String userId;


    public Expence() {}

    public Expence(String title, String description, double amount, LocalDateTime date, String userId) {
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.userId = userId;
        //this.user = user;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    //public User getUser() { return user; }
    //public void setUser(User user) { this.user = user; }
}
