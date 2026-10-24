package com.expensetracker.model;

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
}