package com.expensetracker.model;

import java.math.BigDecimal;
import java.util.List;

// Everything the Subs screen needs
public record SubscriptionOverview(int activeCount, BigDecimal monthlyBurn, BigDecimal dueTotal,
                                   List<Item> items) {
    // daysUntilDue is negative when the payment is overdue
    public record Item(Subscription subscription, String categoryName, long daysUntilDue) {}
}