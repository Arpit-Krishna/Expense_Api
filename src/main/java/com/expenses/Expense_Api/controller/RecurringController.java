package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.RecurringRequest;
import com.expenses.Expense_Api.DTO.RecurringView;
import com.expenses.Expense_Api.services.RecurringService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Monthly payments (EMI, rent...) that are logged as expenses automatically on their due day. */
@RestController
@RequestMapping("/api/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public List<RecurringView> list() {
        return recurringService.list(LocalDate.now());
    }

    @PostMapping
    public RecurringView create(@Valid @RequestBody RecurringRequest request) {
        return recurringService.create(request, LocalDate.now());
    }

    @PutMapping("/{id}")
    public RecurringView update(@PathVariable String id, @Valid @RequestBody RecurringRequest request) {
        return recurringService.update(id, request, LocalDate.now());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        recurringService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Logs this month's payment now, for one that went out early. */
    @PostMapping("/{id}/log")
    public RecurringView logNow(@PathVariable String id) {
        return recurringService.logNow(id, LocalDate.now());
    }

    /** Adds EMI, parents, rent, bills and subscription with editable amounts, if the user has none yet. */
    @PostMapping("/defaults")
    public List<RecurringView> addDefaults() {
        return recurringService.addDefaults(LocalDate.now());
    }
}
