package com.expensetracker.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.dao.SubscriptionDao;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Subscription;

public class SubscriptionService {
    private final SubscriptionDao subscriptionDao = new SubscriptionDao();
    private final ExpenseDao expenseDao = new ExpenseDao();

    // Creates an expense for every due payment, then moves next_due_date forward
    public List<Expense> processDue(int userId, LocalDate today) throws SQLException {
        List<Expense> created = new ArrayList<>();
        for (Subscription s : subscriptionDao.findDue(userId, today)) {
            LocalDate due = s.nextDueDate();
            while (!due.isAfter(today)) {
                created.add(expenseDao.add(new Expense(0, s.userId(), s.categoryId(),
                        s.amount(), s.name() + " (subscription)", due)));
                due = s.frequency().next(due);
            }
            subscriptionDao.updateNextDueDate(s.id(), due);
        }
        return created;
    }
}