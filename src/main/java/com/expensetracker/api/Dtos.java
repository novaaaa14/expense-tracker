package com.expensetracker.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.expensetracker.model.BudgetAlert;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Frequency;
import com.expensetracker.model.User;

// The JSON shapes the app sends and receives (requests first, then responses)
public final class Dtos {
    private Dtos() {}

    // ---- requests ----
    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record ExpenseRequest(Integer categoryId, BigDecimal amount, String description,
                                 LocalDate date, String note) {}
    public record BudgetRequest(BigDecimal limit, Integer alertThresholdPercent) {}
    public record CategoryRequest(String name, String color, String icon, BigDecimal limit) {}
    public record SubscriptionRequest(String name, BigDecimal amount, Integer categoryId,
                                      Frequency frequency, LocalDate nextDueDate) {}
    public record IdsRequest(List<Integer> ids) {}

    // ---- responses ----
    public record AuthResponse(String token, User user) {}
    // alert is null when the expense didn't cross any budget threshold
    public record ExpenseResponse(Expense expense, BudgetAlert alert) {}
    public record DayTotal(LocalDate date, BigDecimal total) {}
    public record CategoryItem(int id, String name, String color, String icon, boolean builtIn,
                               int itemCount, BigDecimal totalSpent, BigDecimal monthlyLimit) {}
    public record CategoryBasic(int id, String name, String color, String icon, boolean builtIn) {}
    public record CountResponse(int count) {}
    public record DeletedResponse(int deleted) {}
    public record ProcessedResponse(int created) {}
}