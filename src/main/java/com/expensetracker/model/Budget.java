package com.expensetracker.model;

import java.math.BigDecimal;

public record Budget(int id, int userId, int categoryId,
                     BigDecimal monthlyLimit, int alertThresholdPercent) {
}