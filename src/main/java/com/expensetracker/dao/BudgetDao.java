package com.expensetracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.Budget;

public class BudgetDao {

    public void save(Budget b) throws SQLException {
        String sql = "INSERT INTO budgets (user_id, category_id, monthly_limit, alert_threshold_percent) "
                   + "VALUES (?, ?, ?, ?) "
                   + "ON CONFLICT (user_id, category_id) DO UPDATE SET "
                   + "monthly_limit = EXCLUDED.monthly_limit, "
                   + "alert_threshold_percent = EXCLUDED.alert_threshold_percent";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, b.userId());
            ps.setInt(2, b.categoryId());
            ps.setBigDecimal(3, b.monthlyLimit());
            ps.setInt(4, b.alertThresholdPercent());
            ps.executeUpdate();
        }
    }

    public Optional<Budget> findByUserAndCategory(int userId, int categoryId) throws SQLException {
        String sql = "SELECT id, user_id, category_id, monthly_limit, alert_threshold_percent "
                   + "FROM budgets WHERE user_id = ? AND category_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public List<Budget> findByUser(int userId) throws SQLException {
        String sql = "SELECT id, user_id, category_id, monthly_limit, alert_threshold_percent "
                   + "FROM budgets WHERE user_id = ?";
        List<Budget> result = new ArrayList<>();
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

    private Budget mapRow(ResultSet rs) throws SQLException {
        return new Budget(rs.getInt("id"), rs.getInt("user_id"), rs.getInt("category_id"),
                rs.getBigDecimal("monthly_limit"), rs.getInt("alert_threshold_percent"));
    }
}