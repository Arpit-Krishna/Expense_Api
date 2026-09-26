package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.ExpenseResponse;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.services.ExpenceServicies;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExpenceController {

    @Autowired
    private ExpenceServicies expenseServicies;

    @PostMapping("/expense")
    public Expence createExpense(@Valid @RequestBody Expence expense) {
        return expenseServicies.addExpense(expense);
    }

    @GetMapping("/expense")
    public ExpenseResponse getExpense(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        return expenseServicies.getMyExpenses(page, size, sortBy, sortDir, category, from, to, q);
    }

    @GetMapping("/expense/{id}")
    public Expence getExpenseById(@PathVariable String id) {
        return expenseServicies.getExpenseById(id);
    }

    @PutMapping("/expense/{id}")
    public Expence updateExpense(@Valid @RequestBody Expence expense, @PathVariable String id) {
        return expenseServicies.updateExpense(id, expense);
    }

    @DeleteMapping("/expense/{id}")
    public ResponseEntity<String> deleteExpense(@PathVariable String id) {
        return ResponseEntity.ok(expenseServicies.deleteExpense(id));
    }

    @GetMapping("/expense/summary")
    public Map<String, Object> getExpenseSummary() {
        return expenseServicies.getExpenseSummary();
    }
}
