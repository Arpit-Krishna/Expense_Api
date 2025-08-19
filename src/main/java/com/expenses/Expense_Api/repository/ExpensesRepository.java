package com.expenses.Expense_Api.repository;

import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ExpensesRepository extends MongoRepository<Expence, String> {
    Page<Expence> findByUserId(String userId, Pageable pageable);
    List<Expence> findByUserId(String userId);

    Optional<Expence> findByUserIdAndId(String userId, String id);

}
