package com.expensetracker.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.CategoryStats;

public class CategoryService {
    private static final int DEFAULT_ALERT_PERCENT = 80;

    private final CategoryDao categoryDao = new CategoryDao();
    private final BudgetDao budgetDao = new BudgetDao();

    public List<CategoryStats> list(int userId) throws SQLException {
        return categoryDao.findStatsByUser(userId);
    }

    // The optional limit is stored as the category's budget (budgets is the single source of truth)
    public Category create(int userId, String name, String color, String icon, BigDecimal limit)
            throws SQLException {
        String cleanName = validate(name, color, icon, limit);
        Category created;
        try {
            created = categoryDao.add(new Category(0, userId, cleanName, color, icon, false));
        } catch (SQLException e) {
            throw friendly(e, cleanName);
        }
        if (limit != null) {
            budgetDao.save(new Budget(0, userId, created.id(), limit, DEFAULT_ALERT_PERCENT));
        }
        return created;
    }

    // Empty if the category doesn't exist. A non-null limit also updates the budget (threshold kept).
    public Optional<Category> update(int userId, int id, String name, String color, String icon, BigDecimal limit)
            throws SQLException {
        String cleanName = validate(name, color, icon, limit);
        Category updated = new Category(id, userId, cleanName, color, icon, false);
        try {
            if (!categoryDao.update(updated)) {
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw friendly(e, cleanName);
        }
        if (limit != null) {
            int threshold = budgetDao.findByUserAndCategory(userId, id)
                    .map(Budget::alertThresholdPercent).orElse(DEFAULT_ALERT_PERCENT);
            budgetDao.save(new Budget(0, userId, id, limit, threshold));
        }
        return categoryDao.findById(id, userId);
    }

    // For the confirm dialog: "N associated expenses will be unlinked"
    public int unlinkCount(int userId, int id) throws SQLException {
        return categoryDao.countExpenses(id, userId);
    }

    // PURGE: returns how many were deleted. Default categories are skipped.
    public int deleteMany(int userId, List<Integer> ids) throws SQLException {
        return categoryDao.deleteMany(ids, userId);
    }

    private static String validate(String name, String color, String icon, BigDecimal limit) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Enter a category name");
        }
        if (cleanName.length() > 50) {
            throw new IllegalArgumentException("Name can be at most 50 characters");
        }
        if (color == null || !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Pick a valid colour");
        }
        if (icon == null || icon.isBlank() || icon.length() > 30) {
            throw new IllegalArgumentException("Pick an icon");
        }
        if (limit != null && limit.signum() <= 0) {
            throw new IllegalArgumentException("Limit must be greater than 0");
        }
        return cleanName;
    }

    // Duplicate name becomes a friendly validation error; anything else is rethrown unchanged
    private static SQLException friendly(SQLException e, String name) {
        if ("23505".equals(e.getSQLState())) {   // unique (user_id, name)
            throw new IllegalArgumentException("You already have a category called " + name);
        }
        return e;
    }
}