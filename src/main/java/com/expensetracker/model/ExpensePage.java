package com.expensetracker.model;

import java.util.List;

public record ExpensePage(List<ExpenseRow> rows, int page, int pageSize,
                          int totalItems, int totalPages) {
}