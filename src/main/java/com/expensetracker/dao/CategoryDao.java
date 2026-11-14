package com.expensetracker.dao;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.Category;
import com.expensetracker.model.CategoryStats;

public class CategoryDao {
    private static final String COLUMNS = "id, user_id, name, color, icon, is_default";

    public Category add(Category c) throws SQLException {
        String sql = "INSERT INTO categories (user_id, name, color, icon, is_default) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, c.userId());
            ps.setString(2, c.name());
            ps.setString(3, c.color());
            ps.setString(4, c.icon());
            ps.setBoolean(5, c.isDefault());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getInt("id"), c.userId(), c.name(), c.color(), c.icon(), c.isDefault());
            }
        }
    }

    public List<Category> findByUser(int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM categories WHERE user_id = ? ORDER BY name";
        List<Category> result = new ArrayList<>();
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

    public Optional<Category> findById(int id, int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM categories WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    // Each category with its all-time item count and total spent (for the Categories screen)
    public List<CategoryStats> findStatsByUser(int userId) throws SQLException {
        String sql = "SELECT c.id, c.user_id, c.name, c.color, c.icon, c.is_default, "
                   + "COUNT(e.id) AS item_count, COALESCE(SUM(e.amount), 0) AS total_spent "
                   + "FROM categories c LEFT JOIN expenses e ON e.category_id = c.id AND e.user_id = c.user_id "
                   + "WHERE c.user_id = ? GROUP BY c.id ORDER BY c.name";
        List<CategoryStats> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CategoryStats(mapRow(rs), rs.getInt("item_count"),
                            rs.getBigDecimal("total_spent")));
                }
            }
        }
        return result;
    }

    // How many expenses would be unlinked if this category were deleted (for the confirm dialog)
    public int countExpenses(int id, int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM expenses WHERE category_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Name, colour and icon only; is_default can't be changed
    public boolean update(Category c) throws SQLException {
        String sql = "UPDATE categories SET name = ?, color = ?, icon = ? WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.name());
            ps.setString(2, c.color());
            ps.setString(3, c.icon());
            ps.setInt(4, c.id());
            ps.setInt(5, c.userId());
            return ps.executeUpdate() > 0;
        }
    }

    // Default categories are locked: they never match, so they are never deleted
    public boolean delete(int id, int userId) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ? AND user_id = ? AND is_default = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // Bulk delete (PURGE). Returns how many were actually deleted; defaults are skipped.
    public int deleteMany(List<Integer> ids, int userId) throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM categories WHERE user_id = ? AND is_default = FALSE AND id = ANY (?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Array array = conn.createArrayOf("integer", ids.toArray());
            ps.setInt(1, userId);
            ps.setArray(2, array);
            return ps.executeUpdate();
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("id"), rs.getInt("user_id"), rs.getString("name"),
                rs.getString("color"), rs.getString("icon"), rs.getBoolean("is_default"));
    }
}