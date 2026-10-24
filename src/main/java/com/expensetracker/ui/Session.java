package com.expensetracker.ui;

import com.expensetracker.model.User;

public final class Session {
    private static User current;

    private Session() {}

    public static void login(User user) { current = user; }
    public static void logout() { current = null; }
    public static User user() { return current; }
    public static int userId() { return current.id(); }
}