package com.expenses.Expense_Api.repository;

import com.expenses.Expense_Api.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BudgetRepository extends MongoRepository<Budget, String> {
    Optional<Budget> findByUserId(String userId);
}
