package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.ExpenseResponse;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.User;
import com.expenses.Expense_Api.notify.LimitAlertService;
import com.expenses.Expense_Api.repository.ExpensesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ExpenceServicies {

    private static final Set<String> SORTABLE = Set.of("date", "amount", "title", "category", "createdAt");

    @Autowired
    ExpensesRepository expensesRepository;
    @Autowired
    private UserServicies userServicies;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private LimitAlertService limitAlerts;

    public Expence addExpense(Expence expense) {
        User currentUser = userServicies.currentUser();
        LocalDateTime now = LocalDateTime.now();

        expense.setId(null);
        expense.setUserId(currentUser.getId());
        expense.setRecurringId(null);
        expense.setTitle(expense.getTitle().trim());
        expense.setCategory(expense.getCategory());
        if (expense.getDate() == null) expense.setDate(now);
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);

        Expence saved = expensesRepository.save(expense);
        limitAlerts.expenseChanged(currentUser.getId(), saved.getDate());
        return saved;
    }

    public ExpenseResponse getMyExpenses(int page, int size, String sortBy, String sortDir,
                                         String category, LocalDate from, LocalDate to, String search) {
        User currentUser = userServicies.currentUser();

        if (!SORTABLE.contains(sortBy)) sortBy = "date";
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), 100);

        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("userId").is(currentUser.getId()));
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("all")) {
            // Older records keep the category in description, so match either field.
            filters.add(new Criteria().orOperator(
                    Criteria.where("category").is(category),
                    new Criteria().andOperator(
                            new Criteria().orOperator(Criteria.where("category").exists(false), Criteria.where("category").is(null)),
                            Criteria.where("description").is(category))));
        }
        if (from != null) filters.add(Criteria.where("date").gte(from.atStartOfDay()));
        if (to != null) filters.add(Criteria.where("date").lt(to.plusDays(1).atStartOfDay()));
        if (search != null && !search.isBlank()) {
            String q = Pattern.quote(search.trim().substring(0, Math.min(search.trim().length(), 100)));
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(q, "i"),
                    Criteria.where("description").regex(q, "i")));
        }

        Query query = new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, Expence.class);

        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort.and(Sort.by("id").descending()));
        List<Expence> content = mongoTemplate.find(query.with(pageable), Expence.class);

        int totalPages = (int) Math.ceil(total / (double) size);
        double totalAmount = total == 0 ? 0 : sumAmount(filters);
        return new ExpenseResponse(content, page, totalPages, total, totalAmount);
    }

    /** Totals the matching expenses inside MongoDB instead of loading every document. */
    private double sumAmount(List<Criteria> filters) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(filters.toArray(new Criteria[0]))),
                Aggregation.group().sum("amount").as("total"));
        Document result = mongoTemplate.aggregate(aggregation, Expence.class, Document.class).getUniqueMappedResult();
        return result == null || !(result.get("total") instanceof Number n) ? 0 : n.doubleValue();
    }

    public Expence getExpenseById(String id) {
        User currentUser = userServicies.currentUser();
        return expensesRepository.findByUserIdAndId(currentUser.getId(), id)
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
    }

    public Expence updateExpense(String expenseId, Expence details) {
        Expence expense = getExpenseById(expenseId);

        expense.setTitle(details.getTitle().trim());
        expense.setAmount(details.getAmount());
        expense.setCategory(details.getCategory());
        expense.setDescription(details.getDescription());
        if (details.getDate() != null) expense.setDate(details.getDate());
        expense.setUpdatedAt(LocalDateTime.now());

        Expence saved = expensesRepository.save(expense);
        limitAlerts.expenseChanged(saved.getUserId(), saved.getDate());
        return saved;
    }

    public String deleteExpense(String id) {
        Expence expense = getExpenseById(id);
        expensesRepository.delete(expense);
        return "Expense deleted successfully";
    }

    public Map<String, Object> getExpenseSummary() {
        User currentUser = userServicies.currentUser();

        List<Expence> expenses = expensesRepository.findByUserId(currentUser.getId());
        expenses.sort((a, b) -> {
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        });

        double totalExpense = expenses.stream().mapToDouble(Expence::getAmount).sum();

        String favoriteCategory = expenses.stream()
                .collect(Collectors.groupingBy(Expence::getCategory, Collectors.summingDouble(Expence::getAmount)))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalExpense", totalExpense);
        summary.put("totalExpenseCount", expenses.size());
        summary.put("favoriteCategory", favoriteCategory);
        summary.put("expenses", expenses);

        return summary;
    }
}
