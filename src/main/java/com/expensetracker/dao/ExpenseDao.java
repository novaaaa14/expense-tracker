package com.expensetracker.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.Expense;

public class ExpenseDao {

    // CREATE: returns the expense with its new database id filled in
    public Expense add(Expense e) throws SQLException {
        String sql = "INSERT INTO expenses (user_id, category_id, amount, description, expense_date) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.userId());
            ps.setInt(2, e.categoryId());
            ps.setBigDecimal(3, e.amount());
            ps.setString(4, e.description());
            ps.setObject(5, e.expenseDate());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Expense(keys.getInt("id"), e.userId(), e.categoryId(),
                                   e.amount(), e.description(), e.expenseDate());
            }
        }
    }

    // READ: all expenses for a user, newest first
    public List<Expense> findByUser(int userId) throws SQLException {
        String sql = "SELECT id, user_id, category_id, amount, description, expense_date "
                   + "FROM expenses WHERE user_id = ? ORDER BY expense_date DESC, id DESC";
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

    // Used by the pie/bar chart: category name -> total spent, biggest first
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

    // UPDATE: returns true if a row was changed
    public boolean update(Expense e) throws SQLException {
        String sql = "UPDATE expenses SET category_id = ?, amount = ?, description = ?, expense_date = ? "
                   + "WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, e.categoryId());
            ps.setBigDecimal(2, e.amount());
            ps.setString(3, e.description());
            ps.setObject(4, e.expenseDate());
            ps.setInt(5, e.id());
            ps.setInt(6, e.userId());
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
                rs.getInt("category_id"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getObject("expense_date", LocalDate.class));
    }
}