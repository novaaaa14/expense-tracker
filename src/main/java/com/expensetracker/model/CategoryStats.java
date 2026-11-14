package com.expensetracker.model;

import java.math.BigDecimal;

public record CategoryStats(Category category, int itemCount, BigDecimal totalSpent) {
}