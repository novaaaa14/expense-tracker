package com.expensetracker.model;

import java.util.Optional;

public record AddExpenseResult(Expense expense, Optional<BudgetAlert> alert) {
}