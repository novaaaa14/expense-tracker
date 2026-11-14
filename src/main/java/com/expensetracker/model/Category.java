package com.expensetracker.model;

public record Category(int id, int userId, String name, String color, String icon, boolean isDefault) {
}