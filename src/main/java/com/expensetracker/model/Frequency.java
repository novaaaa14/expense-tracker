package com.expensetracker.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public enum Frequency {
    WEEKLY, MONTHLY, YEARLY;

    public LocalDate next(LocalDate from) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case YEARLY -> from.plusYears(1);
        };
    }

    // Normalises a per-period amount to a per-month amount (for the burn rate)
    public BigDecimal toMonthly(BigDecimal amount) {
        return switch (this) {
            case WEEKLY -> amount.multiply(BigDecimal.valueOf(52))
                                 .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case MONTHLY -> amount;
            case YEARLY -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        };
    }
}