package com.expensetracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.AddExpenseResult;
import com.expensetracker.model.Budget;
import com.expensetracker.model.BudgetAlert;
import com.expensetracker.model.CategorySpend;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpensePage;
import com.expensetracker.model.MonthSummary;

public class ExpenseService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final BudgetDao budgetDao = new BudgetDao();
    private final CategoryDao categoryDao = new CategoryDao();

    public AddExpenseResult addExpense(Expense e) throws SQLException {
        validate(e);
        Expense saved = expenseDao.add(e);
        Optional<BudgetAlert> alert = checkBudget(e.userId(), e.categoryId(), e.expenseDate());
        return new AddExpenseResult(saved, alert);
    }

    // Edit an existing expense. Empty if it doesn't exist (or isn't this user's).
    public Optional<AddExpenseResult> updateExpense(Expense e) throws SQLException {
        validate(e);
        if (!expenseDao.update(e)) {
            return Optional.empty();
        }
        return Optional.of(new AddExpenseResult(e, checkBudget(e.userId(), e.categoryId(), e.expenseDate())));
    }

    public boolean deleteExpense(int id, int userId) throws SQLException {
        return expenseDao.delete(id, userId);
    }

    // Daily Vault: everything spent on one day
    public BigDecimal totalForDay(int userId, LocalDate day) throws SQLException {
        return expenseDao.totalForRange(userId, day, day);
    }

    // Expense log: one page, optionally filtered to a category. Out-of-range pages are clamped.
    public ExpensePage page(int userId, Integer categoryId, int page, int pageSize) throws SQLException {
        int size = Math.max(1, Math.min(pageSize, 50));
        int total = expenseDao.count(userId, categoryId);
        int totalPages = Math.max(1, (total + size - 1) / size);
        int current = Math.max(1, Math.min(page, totalPages));
        return new ExpensePage(expenseDao.findPage(userId, categoryId, size, (current - 1) * size),
                current, size, total, totalPages);
    }

    // Charts: per-category totals, counts and percentages for a month
    public MonthSummary monthSummary(int userId, YearMonth month) throws SQLException {
        List<CategorySpend> spends = expenseDao.categoryBreakdown(userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal grand = spends.stream().map(CategorySpend::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MonthSummary.Slice> slices = spends.stream()
                .map(s -> new MonthSummary.Slice(s, grand.signum() == 0 ? 0
                        : s.total().multiply(HUNDRED).divide(grand, 0, RoundingMode.HALF_UP).intValue()))
                .toList();
        return new MonthSummary(month, grand, slices);
    }

    // Charts: the "Expenses: <category>" list (categoryId null = Uncategorized)
    public List<Expense> recentForCategory(int userId, Integer categoryId, YearMonth month, int limit)
            throws SQLException {
        return expenseDao.findRecentByCategory(userId, categoryId,
                month.atDay(1), month.atEndOfMonth(), Math.max(1, Math.min(limit, 50)));
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
        int percent = spent.multiply(HUNDRED)
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

    private void validate(Expense e) throws SQLException {
        if (e.amount() == null || e.amount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (e.expenseDate() == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (e.userId() <= 0 || e.categoryId() == null || e.categoryId() <= 0) {
            throw new IllegalArgumentException("User and category are required");
        }
        if (e.description() != null && e.description().length() > 255) {
            throw new IllegalArgumentException("Item name can be at most 255 characters");
        }
        if (e.note() != null && e.note().length() > 255) {
            throw new IllegalArgumentException("Note can be at most 255 characters");
        }
        // stops one user logging an expense against another user's category
        if (categoryDao.findById(e.categoryId(), e.userId()).isEmpty()) {
            throw new IllegalArgumentException("Category not found");
        }
    }
}