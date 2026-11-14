package com.expensetracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.Budget;
import com.expensetracker.model.BudgetStatus;
import com.expensetracker.model.Category;
import com.expensetracker.model.CategorySpend;

public class BudgetService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetDao budgetDao = new BudgetDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final ExpenseDao expenseDao = new ExpenseDao();

    // Create or update the budget for a category
    public Budget save(int userId, int categoryId, BigDecimal limit, int thresholdPercent) throws SQLException {
        if (limit == null || limit.signum() <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException("Alert % must be 1 to 100");
        }
        if (categoryDao.findById(categoryId, userId).isEmpty()) {
            throw new IllegalArgumentException("Category not found");
        }
        Budget budget = new Budget(0, userId, categoryId, limit, thresholdPercent);
        budgetDao.save(budget);
        return budgetDao.findByUserAndCategory(userId, categoryId).orElse(budget);
    }

    // The Budgets screen: one status per budget for the month (the watchdog banner = non-OK ones)
    public List<BudgetStatus> statusForMonth(int userId, YearMonth month) throws SQLException {
        Map<Integer, Category> categories = new HashMap<>();
        categoryDao.findByUser(userId).forEach(c -> categories.put(c.id(), c));

        Map<Integer, BigDecimal> spentByCategory = new HashMap<>();
        for (CategorySpend s : expenseDao.categoryBreakdown(userId, month.atDay(1), month.atEndOfMonth())) {
            if (s.categoryId() != null) {
                spentByCategory.put(s.categoryId(), s.total());
            }
        }

        List<BudgetStatus> result = new ArrayList<>();
        for (Budget b : budgetDao.findByUser(userId)) {
            Category c = categories.get(b.categoryId());
            if (c != null) {
                result.add(toStatus(b, c, spentByCategory.getOrDefault(b.categoryId(), BigDecimal.ZERO)));
            }
        }
        result.sort(Comparator.comparing(BudgetStatus::categoryName));
        return result;
    }

    static BudgetStatus toStatus(Budget b, Category c, BigDecimal spent) {
        BigDecimal limit = b.monthlyLimit();
        // rounded DOWN so 79.96% never displays as "80.0%" while still being under the threshold
        BigDecimal percent = spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.DOWN);

        BudgetStatus.Status status;
        if (spent.compareTo(limit) >= 0) {
            status = BudgetStatus.Status.EXCEEDED;
        } else if (spent.multiply(HUNDRED)
                        .compareTo(limit.multiply(BigDecimal.valueOf(b.alertThresholdPercent()))) >= 0) {
            status = BudgetStatus.Status.WARNING;
        } else {
            status = BudgetStatus.Status.OK;
        }
        return new BudgetStatus(b.id(), c.id(), c.name(), c.color(), c.icon(), spent, limit, percent,
                b.alertThresholdPercent(), limit.subtract(spent), status);
    }
}