package com.expenses.Expense_Api.DTO;

import com.expenses.Expense_Api.model.Expence;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private List<Expence> expenses;
    private int currentPage;
    private int totalPages;
    private long totalItems;

}
