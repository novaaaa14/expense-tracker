package com.expensetracker.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

// Everything the Charts screen needs for one month
public record MonthSummary(YearMonth month, BigDecimal total, List<Slice> slices) {
    public record Slice(CategorySpend spend, int percent) {}
}