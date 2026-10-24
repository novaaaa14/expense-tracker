package com.expensetracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.Category;

public class CategoryDao {

    public Category add(Category c) throws SQLException {
        String sql = "INSERT INTO categories (user_id, name) VALUES (?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.userId());
            ps.setString(2, c.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getInt("id"), c.userId(), c.name());
            }
        }
    }

    public List<Category> findByUser(int userId) throws SQLException {
        String sql = "SELECT id, user_id, name FROM categories WHERE user_id = ? ORDER BY name";
        List<Category> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Category(rs.getInt("id"), rs.getInt("user_id"), rs.getString("name")));
                }
            }
        }
        return result;
    }

    public boolean delete(int id, int userId) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
}