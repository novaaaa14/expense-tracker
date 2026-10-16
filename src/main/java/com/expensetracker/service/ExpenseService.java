package com.expensetracker.service;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.AddExpenseResult;
import com.expensetracker.model.Budget;
import com.expensetracker.model.BudgetAlert;
import com.expensetracker.model.Expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

public class ExpenseService {
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final BudgetDao budgetDao = new BudgetDao();

    public AddExpenseResult addExpense(Expense e) throws SQLException {
        validate(e);
        Expense saved = expenseDao.add(e);
        Optional<BudgetAlert> alert = checkBudget(e.userId(), e.categoryId(), e.expenseDate());
        return new AddExpenseResult(saved, alert);
    }

    // Compares this month's spending in a category against its budget
    public Optional<BudgetAlert> checkBudget(int userId, int categoryId, LocalDate date) throws SQLException {
        Optional<Budget> budget = budgetDao.findByUserAndCategory(userId, categoryId);
        if (budget.isEmpty()) {
            return Optional.empty();   // no budget set for this category
        }

        YearMonth month = YearMonth.from(date);
        BigDecimal spent = expenseDao.totalForCategory(
                userId, categoryId, month.atDay(1), month.atEndOfMonth());

        BigDecimal limit = budget.get().monthlyLimit();
        int threshold = budget.get().alertThresholdPercent();
        int percent = spent.multiply(BigDecimal.valueOf(100))
                           .divide(limit, 0, RoundingMode.DOWN)
                           .intValue();

        if (spent.compareTo(limit) >= 0) {
            return Optional.of(new BudgetAlert(categoryId, spent, limit, percent, BudgetAlert.Status.EXCEEDED));
        }
        if (percent >= threshold) {
            return Optional.of(new BudgetAlert(categoryId, spent, limit, percent, BudgetAlert.Status.WARNING));
        }
        return Optional.empty();
    }

    private void validate(Expense e) {
        if (e.amount() == null || e.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (e.expenseDate() == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (e.userId() <= 0 || e.categoryId() <= 0) {
            throw new IllegalArgumentException("User and category are required");
        }
    }
}