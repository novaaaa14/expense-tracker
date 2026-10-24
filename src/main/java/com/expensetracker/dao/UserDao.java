package com.expensetracker.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import com.expensetracker.config.DatabaseConfig;
import com.expensetracker.model.User;

public class UserDao {

    public record StoredUser(User user, String passwordHash) {}

    public User add(String name, String email, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getInt("id"), name, email);
            }
        }
    }

    public Optional<StoredUser> findByEmail(String email) throws SQLException {
        String sql = "SELECT id, name, email, password_hash FROM users WHERE email = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                User user = new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
                return Optional.of(new StoredUser(user, rs.getString("password_hash")));
            }
        }
    }
}