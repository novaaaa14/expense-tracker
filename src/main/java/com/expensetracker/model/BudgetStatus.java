package com.expensetracker.model;

import java.math.BigDecimal;

// One card on the Budgets screen (this month's spending vs the limit)
public record BudgetStatus(int budgetId, int categoryId, String categoryName, String color, String icon,
                           BigDecimal spent, BigDecimal limit, BigDecimal percent,
                           int alertThresholdPercent, BigDecimal remaining, Status status) {

    public enum Status { OK, WARNING, EXCEEDED }
}