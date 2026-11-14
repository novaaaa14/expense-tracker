package com.expensetracker.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.CategorySpend;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseRow;

public class ExpenseDao {
    private static final String COLUMNS =
            "id, user_id, category_id, amount, description, expense_date, note";

    // CREATE: returns the expense with its new database id filled in
    public Expense add(Expense e) throws SQLException {
        String sql = "INSERT INTO expenses (user_id, category_id, amount, description, expense_date, note) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.userId());
            ps.setObject(2, e.categoryId(), Types.INTEGER);
            ps.setBigDecimal(3, e.amount());
            ps.setString(4, e.description());
            ps.setObject(5, e.expenseDate());
            ps.setString(6, e.note());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Expense(keys.getInt("id"), e.userId(), e.categoryId(),
                                   e.amount(), e.description(), e.expenseDate(), e.note());
            }
        }
    }

    // READ: all expenses for a user, newest first
    public List<Expense> findByUser(int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM expenses WHERE user_id = ? "
                   + "ORDER BY expense_date DESC, id DESC";
        List<Expense> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    // READ: one page of the expense log with category name/colour, optionally filtered by category
    public List<ExpenseRow> findPage(int userId, Integer categoryId, int limit, int offset) throws SQLException {
        String sql = "SELECT e.id, e.user_id, e.category_id, e.amount, e.description, e.expense_date, e.note, "
                   + "c.name AS category_name, c.color AS category_color "
                   + "FROM expenses e LEFT JOIN categories c ON c.id = e.category_id "
                   + "WHERE e.user_id = ? " + (categoryId != null ? "AND e.category_id = ? " : "")
                   + "ORDER BY e.expense_date DESC, e.id DESC LIMIT ? OFFSET ?";
        List<ExpenseRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, userId);
            if (categoryId != null) {
                ps.setInt(i++, categoryId);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ExpenseRow(mapRow(rs),
                            rs.getString("category_name"), rs.getString("category_color")));
                }
            }
        }
        return result;
    }

    // Total rows for pagination (same optional category filter as findPage)
    public int count(int userId, Integer categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM expenses WHERE user_id = ?"
                   + (categoryId != null ? " AND category_id = ?" : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (categoryId != null) {
                ps.setInt(2, categoryId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Everything spent in a date range, all categories (Daily Vault uses from = to = today)
    public BigDecimal totalForRange(int userId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses "
                   + "WHERE user_id = ? AND expense_date BETWEEN ? AND ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Used by budget alerts: how much spent in one category in a date range
    public BigDecimal totalForCategory(int userId, int categoryId,
                                       LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM expenses "
                   + "WHERE user_id = ? AND category_id = ? AND expense_date BETWEEN ? AND ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, categoryId);
            ps.setObject(3, from);
            ps.setObject(4, to);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    // Category name -> total spent, biggest first (kept from the old chart)
    public Map<String, BigDecimal> totalsByCategory(int userId,
                                                    LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT c.name, SUM(e.amount) AS total "
                   + "FROM expenses e JOIN categories c ON c.id = e.category_id "
                   + "WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ? "
                   + "GROUP BY c.name ORDER BY total DESC";
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("name"), rs.getBigDecimal("total"));
                }
            }
        }
        return result;
    }

    // Charts + Budgets: per-category total and transaction count for a range, biggest first.
    // Expenses whose category was deleted are grouped as "Uncategorized" (categoryId = null).
    public List<CategorySpend> categoryBreakdown(int userId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT c.id AS category_id, COALESCE(c.name, 'Uncategorized') AS name, "
                   + "COALESCE(c.color, '#C8B6FF') AS color, COALESCE(c.icon, 'tag') AS icon, "
                   + "SUM(e.amount) AS total, COUNT(*) AS cnt "
                   + "FROM expenses e LEFT JOIN categories c ON c.id = e.category_id "
                   + "WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ? "
                   + "GROUP BY c.id, c.name, c.color, c.icon ORDER BY total DESC";
        List<CategorySpend> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CategorySpend(rs.getObject("category_id", Integer.class),
                            rs.getString("name"), rs.getString("color"), rs.getString("icon"),
                            rs.getBigDecimal("total"), rs.getInt("cnt")));
                }
            }
        }
        return result;
    }

    // Charts: newest expenses of one category within a range (categoryId null = Uncategorized)
    public List<Expense> findRecentByCategory(int userId, Integer categoryId,
                                              LocalDate from, LocalDate to, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM expenses WHERE user_id = ? AND "
                   + (categoryId == null ? "category_id IS NULL" : "category_id = ?")
                   + " AND expense_date BETWEEN ? AND ? ORDER BY expense_date DESC, id DESC LIMIT ?";
        List<Expense> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, userId);
            if (categoryId != null) {
                ps.setInt(i++, categoryId);
            }
            ps.setObject(i++, from);
            ps.setObject(i++, to);
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    // UPDATE: returns true if a row was changed
    public boolean update(Expense e) throws SQLException {
        String sql = "UPDATE expenses SET category_id = ?, amount = ?, description = ?, "
                   + "expense_date = ?, note = ? WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, e.categoryId(), Types.INTEGER);
            ps.setBigDecimal(2, e.amount());
            ps.setString(3, e.description());
            ps.setObject(4, e.expenseDate());
            ps.setString(5, e.note());
            ps.setInt(6, e.id());
            ps.setInt(7, e.userId());
            return ps.executeUpdate() > 0;
        }
    }

    // DELETE: returns true if a row was removed
    public boolean delete(int id, int userId) throws SQLException {
        String sql = "DELETE FROM expenses WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // Turns one row of a ResultSet into an Expense object
    private Expense mapRow(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getObject("category_id", Integer.class),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("note"));
    }
}