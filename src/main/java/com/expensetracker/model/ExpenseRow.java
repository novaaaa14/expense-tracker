package com.expensetracker.model;

// One row of the expense log: the expense plus its category's display info
public record ExpenseRow(Expense expense, String categoryName, String categoryColor) {
}