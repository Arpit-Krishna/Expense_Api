package com.expenses.Expense_Api.repository;

import com.expenses.Expense_Api.model.RecurringPayment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RecurringPaymentRepository extends MongoRepository<RecurringPayment, String> {
    List<RecurringPayment> findByUserIdOrderByDayOfMonthAsc(String userId);

    Optional<RecurringPayment> findByUserIdAndId(String userId, String id);

    List<RecurringPayment> findByActiveTrue();

    long countByUserId(String userId);
}
