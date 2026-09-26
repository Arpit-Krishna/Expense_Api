package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.BudgetStatus;
import com.expenses.Expense_Api.model.Budget;
import com.expenses.Expense_Api.services.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    @Autowired
    private BudgetService budgetService;

    @GetMapping
    public Budget getBudget() {
        return budgetService.getBudget();
    }

    @PutMapping
    public Budget saveBudget(@RequestBody Budget budget) {
        return budgetService.saveBudget(budget);
    }

    /**
     * @param month YYYY-MM, defaults to the current month
     * @param today the client's local date, so "this month" matches the user's calendar
     */
    @GetMapping("/status")
    public BudgetStatus getStatus(@RequestParam(required = false) String month,
                                  @RequestParam(required = false) LocalDate today) {
        return budgetService.getStatus(month, today == null ? LocalDate.now() : today);
    }
}
