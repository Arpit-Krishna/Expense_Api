package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.ExpenseResponse;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.services.ExpenceServicies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExpenceController {

    @Autowired
    private ExpenceServicies expenseServicies;

    @PostMapping("/expense")
    public Expence createExpense(HttpServletRequest request, @RequestBody Expence expense){
        return expenseServicies.addExpense(request, expense);
    }

    @GetMapping("/expense")
    public ExpenseResponse getExpense(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10")   int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest request){
        return expenseServicies.getMyExpenses(request, page, size, sortBy, sortDir);
    }

    @GetMapping("/expense/{id}")
    public Expence getExpenseById(HttpServletRequest request, @PathVariable String id){
        return expenseServicies.getExpenseById(request, id);
    }

    @PutMapping("/expense/{id}")
    public Expence updateExpense(HttpServletRequest request, @RequestBody Expence expense, @PathVariable String id){
        String token = request.getHeader("Authorization").substring(7);
        return expenseServicies.updateExpense(token, id, expense);
    }

    @DeleteMapping("/expense/{id}")
    public ResponseEntity<String> deleteExpense(HttpServletRequest request, @PathVariable String id){
        String message = expenseServicies.deleteExpense(request, id);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/expense/summary")
    public Map<String, Object> getExpenseSummary(HttpServletRequest request) {
        return expenseServicies.getExpenseSummary(request);
    }


}
