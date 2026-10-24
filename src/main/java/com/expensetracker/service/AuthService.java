package com.expensetracker.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.model.Category;
import com.expensetracker.model.User;
import com.expensetracker.util.PasswordHasher;

public class AuthService {
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("Food", "Transport", "Entertainment", "Bills");

    private final UserDao userDao = new UserDao();
    private final CategoryDao categoryDao = new CategoryDao();

    public User register(String name, String email, String password) throws SQLException {
        String cleanName = name.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Enter your name");
        }
        if (!cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        User user;
        try {
            user = userDao.add(cleanName, cleanEmail, PasswordHasher.hash(password));
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {   // unique violation on email
                throw new IllegalArgumentException("An account with this email already exists");
            }
            throw e;
        }

        for (String category : DEFAULT_CATEGORIES) {
            categoryDao.add(new Category(0, user.id(), category));
        }
        return user;
    }

    public Optional<User> login(String email, String password) throws SQLException {
        return userDao.findByEmail(email.trim().toLowerCase())
                .filter(stored -> PasswordHasher.verify(password, stored.passwordHash()))
                .map(UserDao.StoredUser::user);
    }
}