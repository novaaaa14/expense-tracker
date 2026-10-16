package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Expense(
        int id,
        int userId,
        int categoryId,
        BigDecimal amount,
        String description,
        LocalDate expenseDate) {
}