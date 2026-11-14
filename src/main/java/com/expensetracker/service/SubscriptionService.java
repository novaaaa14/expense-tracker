package com.expensetracker.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.dao.SubscriptionDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Subscription;
import com.expensetracker.model.SubscriptionOverview;

public class SubscriptionService {
    private final SubscriptionDao subscriptionDao = new SubscriptionDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final UserDao userDao = new UserDao();

    public Subscription add(Subscription s) throws SQLException {
        if (s.name() == null || s.name().isBlank()) {
            throw new IllegalArgumentException("Enter a service name");
        }
        if (s.name().trim().length() > 100) {
            throw new IllegalArgumentException("Name can be at most 100 characters");
        }
        if (s.amount() == null || s.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (s.frequency() == null) {
            throw new IllegalArgumentException("Pick a frequency");
        }
        if (s.nextDueDate() == null) {
            throw new IllegalArgumentException("Pick the next due date");
        }
        if (s.categoryId() == null || categoryDao.findById(s.categoryId(), s.userId()).isEmpty()) {
            throw new IllegalArgumentException("Pick a category");
        }
        return subscriptionDao.add(new Subscription(0, s.userId(), s.categoryId(), s.name().trim(),
                s.amount(), s.frequency(), s.nextDueDate(), true));
    }

    // The whole Subs screen: list with countdowns, active count, burn rate, and what PROCESS DUE would add
    public SubscriptionOverview overview(int userId, LocalDate today) throws SQLException {
        Map<Integer, String> categoryNames = new HashMap<>();
        categoryDao.findByUser(userId).forEach(c -> categoryNames.put(c.id(), c.name()));

        List<Subscription> all = subscriptionDao.findByUser(userId);
        BigDecimal burn = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        int active = 0;
        List<SubscriptionOverview.Item> items = new ArrayList<>();

        for (Subscription s : all) {
            if (s.active()) {
                active++;
                burn = burn.add(s.frequency().toMonthly(s.amount()));
                due = due.add(dueAmount(s, today));
            }
            items.add(new SubscriptionOverview.Item(s,
                    s.categoryId() == null ? null : categoryNames.get(s.categoryId()),
                    ChronoUnit.DAYS.between(today, s.nextDueDate())));
        }
        return new SubscriptionOverview(active, burn, due, items);
    }

    // Creates an expense for every due payment, then moves next_due_date forward
    public List<Expense> processDue(int userId, LocalDate today) throws SQLException {
        List<Expense> created = new ArrayList<>();
        for (Subscription s : subscriptionDao.findDue(userId, today)) {
            LocalDate due = s.nextDueDate();
            while (!due.isAfter(today)) {
                created.add(expenseDao.add(new Expense(0, s.userId(), s.categoryId(),
                        s.amount(), s.name() + " (subscription)", due, null)));
                due = s.frequency().next(due);
            }
            subscriptionDao.updateNextDueDate(s.id(), due);
        }
        return created;
    }

    // For the scheduled server job: processes every user. One user failing doesn't stop the rest.
    public int processAllDue(LocalDate today) throws SQLException {
        int total = 0;
        for (int userId : userDao.findAllIds()) {
            try {
                total += processDue(userId, today).size();
            } catch (SQLException e) {
                System.err.println("Subscription job failed for user " + userId + ": " + e.getMessage());
            }
        }
        return total;
    }

    public int deleteMany(int userId, List<Integer> ids) throws SQLException {
        return subscriptionDao.deleteMany(ids, userId);
    }

    public boolean delete(int userId, int id) throws SQLException {
        return subscriptionDao.delete(id, userId);
    }

    // Same loop as processDue, but only adds up the amounts (a sub can have several missed payments)
    private static BigDecimal dueAmount(Subscription s, LocalDate today) {
        BigDecimal sum = BigDecimal.ZERO;
        LocalDate due = s.nextDueDate();
        while (!due.isAfter(today)) {
            sum = sum.add(s.amount());
            due = s.frequency().next(due);
        }
        return sum;
    }
}