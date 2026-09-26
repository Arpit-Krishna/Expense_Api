package com.expenses.Expense_Api.repository;

import com.expenses.Expense_Api.model.Expence;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpensesRepository extends MongoRepository<Expence, String> {
    List<Expence> findByUserId(String userId);

    List<Expence> findByUserIdAndDateBetween(String userId, LocalDateTime from, LocalDateTime to);

    Optional<Expence> findByUserIdAndId(String userId, String id);
}
