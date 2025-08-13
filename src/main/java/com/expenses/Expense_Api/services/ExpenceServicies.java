package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.ExpenseResponse;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.repository.ExpensesRepository;
import com.expenses.Expense_Api.repository.UserRepository;
import com.expenses.Expense_Api.util.JwTUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExpenceServicies {

    @Autowired
    ExpensesRepository expensesRepository;
    @Autowired
    private UserServicies userServicies;
    @Autowired
    private JwTUtil jwTUtil;
    @Autowired
    private UserRepository userRepository;

    public Expence addExpense(HttpServletRequest request, Expence expense) {
        User currentUser = userServicies.getCurrentUser(request)
                .orElseThrow(() -> new RuntimeException("User not found"));

        expense.setUser(currentUser);
        expense.setDate(LocalDateTime.now());

        return expensesRepository.save(expense);
    }

    public ExpenseResponse getMyExpenses(HttpServletRequest request, int page, int size, String sortBy, String sortDir) {
        User currentUser = userServicies.getCurrentUser(request)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Expence> expensePage = expensesRepository.findByUser(currentUser, pageable);
        return new ExpenseResponse(
                expensePage.getContent(),
                expensePage.getNumber(),
                expensePage.getTotalPages(),
                expensePage.getTotalElements()
        );
    }

    public Expence getExpenseById(HttpServletRequest request, String id) {
        User currentUser = userServicies.getCurrentUser(request)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return expensesRepository.findByUserAndId(currentUser, id).orElseThrow(() -> new RuntimeException("Expense not found"));
    }

    public Expence updateExpense(String token, String expenseId, Expence expenseDetails) {

        String username = jwTUtil.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Expence expense = expensesRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You cannot update this expense");
        }

        expense.setTitle(expenseDetails.getTitle());
        expense.setAmount(expenseDetails.getAmount());
        expense.setDate(expenseDetails.getDate());

        return expensesRepository.save(expense);
    }

    public String deleteExpense(HttpServletRequest request, String id) {
        String token = request.getHeader("Authorization").substring(7);
        String username = jwTUtil.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Expence expense = expensesRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You cannot delete this expense");
        }

        expensesRepository.delete(expense);

        return "Expense deleted successfully";
    }

    public Map<String, Object> getExpenseSummary(HttpServletRequest request) {
        User currentUser = userServicies.getCurrentUser(request)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Expence> expenses = expensesRepository.findByUser(currentUser);

        double totalExpense = expenses.stream()
                .mapToDouble(Expence::getAmount)
                .sum();

        int totalExpenseCount = expenses.size();

        String favoriteCategory = expenses.stream()
                .collect(Collectors.groupingBy(
                        Expence::getDescription,
                        Collectors.summingDouble(Expence::getAmount)
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalExpense", totalExpense);
        summary.put("totalExpenseCount", totalExpenseCount);
        summary.put("favoriteCategory", favoriteCategory);
        summary.put("expenses", expenses);

        return summary;
    }
}



