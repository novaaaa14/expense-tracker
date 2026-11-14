package com.expensetracker.model;

// Everything the app needs to draw the Budget Pet / Buddy Bot (all computed from existing data)
public record PetStatus(int level, String title, int xp, int xpIntoLevel, int xpForLevel, int levelPercent,
                        int streakDays, int hunger, int moodPercent, String mood,
                        String message, String budgetTip) {
}