package com.expensetracker.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.dao.SubscriptionDao;
import com.expensetracker.model.BudgetStatus;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseRow;
import com.expensetracker.model.PetStatus;

// The pet has no tables of its own: XP, level, mood and streak are all worked out from the
// user's real data, so there is nothing to keep in sync.
public class PetService {
    private static final int XP_PER_EXPENSE = 10;
    private static final int XP_PER_BUDGET = 25;
    private static final int XP_PER_SUBSCRIPTION = 15;
    private static final String[] TITLES = {"HATCHLING", "ROOKIE", "SAVER", "TRACKER", "COLLECTOR",
            "KEEPER", "ARCHIVIST", "STRATEGIST", "MASTER", "LEGEND"};

    private final ExpenseDao expenseDao = new ExpenseDao();
    private final SubscriptionDao subscriptionDao = new SubscriptionDao();
    private final BudgetService budgetService = new BudgetService();

    public PetStatus status(int userId, LocalDate today) throws SQLException {
        int expenseCount = expenseDao.count(userId, null);
        int subscriptionCount = subscriptionDao.findByUser(userId).size();
        List<BudgetStatus> budgets = budgetService.statusForMonth(userId, YearMonth.from(today));
        List<LocalDate> days = expenseDao.recentExpenseDays(userId, today, 400);
        Expense latest = expenseDao.findPage(userId, null, 1, 0).stream()
                .findFirst().map(ExpenseRow::expense).orElse(null);

        int xp = xpFor(expenseCount, budgets.size(), subscriptionCount);
        int level = levelFor(xp);
        int floor = levelThreshold(level);
        int span = levelThreshold(level + 1) - floor;
        int into = xp - floor;

        int streak = streak(days, today);
        int daysSince = days.isEmpty() ? -1 : (int) ChronoUnit.DAYS.between(days.get(0), today);
        int hunger = hungerLevel(daysSince);
        int mood = (int) Math.round(0.6 * budgetHealth(budgets) + 0.4 * hunger);

        return new PetStatus(level, TITLES[Math.min(level - 1, TITLES.length - 1)], xp, into, span,
                into * 100 / span, streak, hunger, mood, moodLabel(mood),
                companionMessage(expenseCount, latest, today, daysSince, streak), budgetTip(budgets));
    }

    // ---- pure rules (package-private so they are easy to test) ----

    static int xpFor(int expenses, int budgets, int subscriptions) {
        return expenses * XP_PER_EXPENSE + budgets * XP_PER_BUDGET + subscriptions * XP_PER_SUBSCRIPTION;
    }

    // XP needed to reach a level: L1 = 0, L2 = 100, L3 = 300, L4 = 600, L5 = 1000, ...
    static int levelThreshold(int level) {
        return 50 * level * (level - 1);
    }

    static int levelFor(int xp) {
        int level = 1;
        while (levelThreshold(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    // Consecutive days with an expense. Today not logged yet doesn't break a streak that ran up to yesterday.
    // days must be distinct and newest first.
    static int streak(List<LocalDate> days, LocalDate today) {
        if (days.isEmpty()) {
            return 0;
        }
        LocalDate expected = days.get(0).equals(today) ? today : today.minusDays(1);
        int count = 0;
        for (LocalDate d : days) {
            if (d.equals(expected)) {
                count++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return count;
    }

    // 100 = just fed today, -20 per day without an expense, 0 if nothing was ever logged
    static int hungerLevel(int daysSinceLast) {
        return daysSinceLast < 0 ? 0 : Math.max(0, 100 - 20 * daysSinceLast);
    }

    // 100 with no trouble; -30 per exceeded budget, -12 per budget in warning
    static int budgetHealth(List<BudgetStatus> budgets) {
        int health = 100;
        for (BudgetStatus b : budgets) {
            if (b.status() == BudgetStatus.Status.EXCEEDED) {
                health -= 30;
            } else if (b.status() == BudgetStatus.Status.WARNING) {
                health -= 12;
            }
        }
        return Math.max(0, health);
    }

    static String moodLabel(int percent) {
        if (percent >= 90) return "OVERJOYED";
        if (percent >= 70) return "HAPPY";
        if (percent >= 50) return "OKAY";
        if (percent >= 30) return "WORRIED";
        return "GLOOMY";
    }

    // The pet's line on the Expenses screen
    static String companionMessage(int expenseCount, Expense latest, LocalDate today, int daysSince, int streak) {
        if (expenseCount == 0) {
            return "Feed me coins! Log your first expense to get me going.";
        }
        if (latest != null && latest.expenseDate().equals(today)) {
            return itemName(latest) + " logged! Your piggy pet is satisfied!";
        }
        if (daysSince >= 2) {
            return "I'm hungry! It's been " + daysSince + " days since your last expense.";
        }
        if (streak >= 3) {
            return streak + "-day streak! Keep feeding me coins!";
        }
        return "All quiet. Log today's spending to keep me happy!";
    }

    // The pet's line on the Budgets screen
    static String budgetTip(List<BudgetStatus> budgets) {
        Optional<BudgetStatus> exceeded = worst(budgets, BudgetStatus.Status.EXCEEDED);
        if (exceeded.isPresent()) {
            String name = exceeded.get().categoryName();
            return "Feed me coins! Skip one " + name.toLowerCase() + " purchase today to cool down your "
                    + name + " meter.";
        }
        Optional<BudgetStatus> warning = worst(budgets, BudgetStatus.Status.WARNING);
        if (warning.isPresent()) {
            return warning.get().categoryName() + " hit " + warning.get().percent().toPlainString()
                    + "% of its budget. Easy does it!";
        }
        if (budgets.isEmpty()) {
            return "Set a budget and I'll keep watch over it for you!";
        }
        return "All budgets are on track. Nice work, trainer!";
    }

    private static Optional<BudgetStatus> worst(List<BudgetStatus> budgets, BudgetStatus.Status status) {
        return budgets.stream()
                .filter(b -> b.status() == status)
                .max((a, b) -> a.percent().compareTo(b.percent()));
    }

    private static String itemName(Expense e) {
        String text = e.description() == null ? "" : e.description().trim();
        if (text.endsWith(" (subscription)")) {   // auto-created by the subscription job
            text = text.substring(0, text.length() - " (subscription)".length()).trim();
        }
        if (text.isEmpty()) {
            return "Expense";
        }
        if (text.length() > 24) {
            text = text.substring(0, 24).trim() + "...";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
