package com.expensetracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.Frequency;
import com.expensetracker.model.Subscription;

public class SubscriptionDao {

    public Subscription add(Subscription s) throws SQLException {
        String sql = "INSERT INTO subscriptions (user_id, category_id, name, amount, frequency, next_due_date, active) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.userId());
            ps.setInt(2, s.categoryId());
            ps.setString(3, s.name());
            ps.setBigDecimal(4, s.amount());
            ps.setString(5, s.frequency().name());
            ps.setObject(6, s.nextDueDate());
            ps.setBoolean(7, s.active());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Subscription(keys.getInt("id"), s.userId(), s.categoryId(), s.name(),
                        s.amount(), s.frequency(), s.nextDueDate(), s.active());
            }
        }
    }

    public List<Subscription> findByUser(int userId) throws SQLException {
        return query("WHERE user_id = ? ORDER BY next_due_date", userId, null);
    }

    // Active subscriptions whose due date has arrived (due date <= asOf)
    public List<Subscription> findDue(int userId, LocalDate asOf) throws SQLException {
        return query("WHERE user_id = ? AND active = TRUE AND next_due_date <= ? ORDER BY next_due_date",
                userId, asOf);
    }

    public void updateNextDueDate(int id, LocalDate next) throws SQLException {
        String sql = "UPDATE subscriptions SET next_due_date = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, next);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public boolean delete(int id, int userId) throws SQLException {
        String sql = "DELETE FROM subscriptions WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // Shared SELECT used by findByUser and findDue
    private List<Subscription> query(String whereClause, int userId, LocalDate asOf) throws SQLException {
        String sql = "SELECT id, user_id, category_id, name, amount, frequency, next_due_date, active "
                   + "FROM subscriptions " + whereClause;
        List<Subscription> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (asOf != null) {
                ps.setObject(2, asOf);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Subscription(
                            rs.getInt("id"), rs.getInt("user_id"), rs.getInt("category_id"),
                            rs.getString("name"), rs.getBigDecimal("amount"),
                            Frequency.valueOf(rs.getString("frequency")),
                            rs.getObject("next_due_date", LocalDate.class),
                            rs.getBoolean("active")));
                }
            }
        }
        return result;
    }
}