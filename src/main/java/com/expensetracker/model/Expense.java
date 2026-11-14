package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// categoryId is null when the expense's category was deleted ("unlinked")
public record Expense(
        int id,
        int userId,
        Integer categoryId,
        BigDecimal amount,
        String description,
        LocalDate expenseDate,
        String note) {
}