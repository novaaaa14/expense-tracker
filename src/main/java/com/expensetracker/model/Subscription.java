package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Subscription(int id, int userId, int categoryId, String name,
                           BigDecimal amount, Frequency frequency,
                           LocalDate nextDueDate, boolean active) {
}