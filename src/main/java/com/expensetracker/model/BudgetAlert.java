package com.expensetracker.model;

import java.math.BigDecimal;

public record BudgetAlert(int categoryId, BigDecimal spent, BigDecimal limit,
                          int percentUsed, Status status) {

    public enum Status { WARNING, EXCEEDED }
}