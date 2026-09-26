package com.expenses.Expense_Api.services;

import com.expenses.Expense_Api.DTO.RecurringRequest;
import com.expenses.Expense_Api.DTO.RecurringView;
import com.expenses.Expense_Api.exception.ApiException;
import com.expenses.Expense_Api.model.Expence;
import com.expenses.Expense_Api.model.RecurringPayment;
import com.expenses.Expense_Api.notify.LimitAlertService;
import com.expenses.Expense_Api.repository.ExpensesRepository;
import com.expenses.Expense_Api.repository.RecurringPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);
    static final int MAX_PER_USER = 50;
    /** Auto-logged payments are dated at this time on their due day. */
    private static final LocalTime POST_TIME = LocalTime.of(9, 0);

    /** Starting set for a new user, matching the fixed categories on the Budgets page. Due days are editable. */
    static final List<RecurringRequest> DEFAULTS = List.of(
            new RecurringRequest("Loan EMI", "EMI", 15000, 1, true, null),
            new RecurringRequest("Money to parents", "Parents", 15000, 1, true, null),
            new RecurringRequest("Rent", "Rent", 9000, 1, true, null),
            new RecurringRequest("Bills", "Bills", 3000, 1, true, null),
            new RecurringRequest("Claude subscription", "Subscriptions", 2000, 1, true, null));

    private final RecurringPaymentRepository repository;
    private final ExpensesRepository expensesRepository;
    private final MongoTemplate mongoTemplate;
    private final UserServicies userServicies;
    private final LimitAlertService limitAlerts;

    public RecurringService(RecurringPaymentRepository repository, ExpensesRepository expensesRepository,
                            MongoTemplate mongoTemplate, UserServicies userServicies, LimitAlertService limitAlerts) {
        this.repository = repository;
        this.expensesRepository = expensesRepository;
        this.mongoTemplate = mongoTemplate;
        this.userServicies = userServicies;
        this.limitAlerts = limitAlerts;
    }

    /** Lists the user's payments, logging any that fell due while the server was asleep. */
    public List<RecurringView> list(LocalDate today) {
        String userId = userServicies.currentUser().getId();
        postDue(repository.findByUserIdOrderByDayOfMonthAsc(userId), today);
        return repository.findByUserIdOrderByDayOfMonthAsc(userId).stream().map(p -> RecurringView.of(p, today)).toList();
    }

    public RecurringView create(RecurringRequest request, LocalDate today) {
        String userId = userServicies.currentUser().getId();
        if (repository.countByUserId(userId) >= MAX_PER_USER) {
            throw ApiException.badRequest("You can have at most " + MAX_PER_USER + " recurring payments");
        }
        RecurringPayment p = new RecurringPayment();
        p.setUserId(userId);
        p.setCreatedAt(LocalDateTime.now());
        apply(p, request);
        skipIfAlreadyDue(p, today);
        return RecurringView.of(repository.save(p), today);
    }

    public RecurringView update(String id, RecurringRequest request, LocalDate today) {
        RecurringPayment p = find(id);
        int oldDay = p.getDayOfMonth();
        apply(p, request);
        // Moving the due day to one already past this month should not log it again or suddenly log it.
        if (oldDay != p.getDayOfMonth()) skipIfAlreadyDue(p, today);
        return RecurringView.of(repository.save(p), today);
    }

    public void delete(String id) {
        repository.delete(find(id));
    }

    /** "Log it now" for this month, e.g. when the payment went out early. Does nothing if already logged. */
    public RecurringView logNow(String id, LocalDate today) {
        RecurringPayment p = find(id);
        post(p, YearMonth.from(today), today);
        return RecurringView.of(find(id), today);
    }

    /** Adds the usual fixed payments for a user who has none yet. */
    public List<RecurringView> addDefaults(LocalDate today) {
        String userId = userServicies.currentUser().getId();
        if (repository.countByUserId(userId) == 0) {
            for (RecurringRequest r : DEFAULTS) {
                RecurringPayment p = new RecurringPayment();
                p.setUserId(userId);
                p.setCreatedAt(LocalDateTime.now());
                apply(p, r);
                skipIfAlreadyDue(p, today);
                repository.save(p);
            }
        }
        return repository.findByUserIdOrderByDayOfMonthAsc(userId).stream().map(p -> RecurringView.of(p, today)).toList();
    }

    /** Scheduler entry point: logs every active payment that is due this month and not logged yet. */
    public int postAllDue(LocalDate today) {
        return postDue(repository.findByActiveTrue(), today);
    }

    private int postDue(List<RecurringPayment> payments, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        int posted = 0;
        for (RecurringPayment p : payments) {
            if (!p.isActive() || p.postedFor(month) || p.dueDate(month).isAfter(today)) continue;
            try {
                if (post(p, month, today)) posted++;
            } catch (RuntimeException e) {
                log.warn("Could not log recurring payment {}: {}", p.getId(), e.getMessage());
            }
        }
        return posted;
    }

    /**
     * Claims the month on the payment first (an atomic update that only succeeds once), then adds the
     * expense, so two runs at the same time can never log it twice.
     */
    private boolean post(RecurringPayment p, YearMonth month, LocalDate today) {
        String ym = month.toString();
        Query unclaimed = new Query(Criteria.where("_id").is(p.getId()).and("lastPostedMonth").ne(ym));
        RecurringPayment claimed = mongoTemplate.findAndModify(unclaimed, new Update().set("lastPostedMonth", ym),
                FindAndModifyOptions.options().returnNew(true), RecurringPayment.class);
        if (claimed == null) return false;

        LocalDate due = p.dueDate(month);
        LocalDateTime now = LocalDateTime.now();
        Expence e = new Expence();
        e.setUserId(p.getUserId());
        e.setTitle(p.getTitle());
        e.setCategory(p.getCategory());
        e.setAmount(p.getAmount());
        e.setDescription(p.getNote());
        e.setDate((due.isAfter(today) ? today : due).atTime(POST_TIME));
        e.setRecurringId(p.getId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        try {
            expensesRepository.save(e);
        } catch (RuntimeException ex) {
            // Release the claim so the next run tries again.
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(p.getId()).and("lastPostedMonth").is(ym)),
                    new Update().set("lastPostedMonth", p.getLastPostedMonth()), RecurringPayment.class);
            throw ex;
        }
        p.setLastPostedMonth(ym);
        limitAlerts.expenseChanged(p.getUserId(), e.getDate());
        return true;
    }

    private static void skipIfAlreadyDue(RecurringPayment p, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        // A payment whose day has already passed this month was most likely paid and logged by hand.
        if (p.dueDate(month).isBefore(today) && !p.postedFor(month)) p.setLastPostedMonth(month.toString());
    }

    private RecurringPayment find(String id) {
        String userId = userServicies.currentUser().getId();
        return repository.findByUserIdAndId(userId, id).orElseThrow(() -> ApiException.notFound("Recurring payment not found"));
    }

    private static void apply(RecurringPayment p, RecurringRequest r) {
        p.setTitle(r.title().trim());
        p.setCategory(Optional.ofNullable(r.category()).map(String::trim).filter(c -> !c.isEmpty()).orElse("Other"));
        p.setAmount(r.amount());
        p.setDayOfMonth(r.dayOfMonth());
        if (r.active() != null) p.setActive(r.active());
        p.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
    }
}
