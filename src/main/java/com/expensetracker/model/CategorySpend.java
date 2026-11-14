package com.expensetracker.model;

import java.math.BigDecimal;

// Spending in one category over a date range. categoryId is null for the "Uncategorized" group.
public record CategorySpend(Integer categoryId, String name, String color, String icon,
                            BigDecimal total, int count) {
}