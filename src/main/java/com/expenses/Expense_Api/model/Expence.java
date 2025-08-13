package com.expenses.Expense_Api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "Expence")
public class Expence {
    @Id
    private String id;

    private String title;
    private String description;
    private double amount;
    private LocalDateTime date;

    @DBRef
    private User user;

    public Expence() {}

    public Expence(String title, String description, double amount, LocalDateTime date, User user) {
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.user = user;
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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
