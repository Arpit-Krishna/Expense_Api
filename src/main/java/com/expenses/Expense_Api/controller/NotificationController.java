package com.expenses.Expense_Api.controller;

import com.expenses.Expense_Api.DTO.NotificationRequest;
import com.expenses.Expense_Api.DTO.NotificationView;
import com.expenses.Expense_Api.notify.NotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationView get() {
        return notificationService.get();
    }

    @PutMapping
    public NotificationView save(@Valid @RequestBody NotificationRequest request) {
        return notificationService.save(request);
    }

    @PostMapping("/test")
    public Map<String, String> test() {
        notificationService.sendTest();
        return Map.of("message", "Test email sent");
    }
}
