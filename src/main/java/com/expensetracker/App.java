/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.expensetracker;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.AddExpenseResult;
import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;

public class App {
    public static void main(String[] args) {
        try {
            ExpenseService service = new ExpenseService();
            ExpenseDao dao = new ExpenseDao();
            LocalDate today = LocalDate.now();

            AddExpenseResult r1 = service.addExpense(new Expense(0, 1, 1, new BigDecimal("40.00"), "Dinner out", today));
            System.out.println("1: " + r1.alert());

            AddExpenseResult r2 = service.addExpense(new Expense(0, 1, 1, new BigDecimal("100.00"), "Party", today));
            System.out.println("2: " + r2.alert());

            AddExpenseResult r3 = service.addExpense(new Expense(0, 1, 2, new BigDecimal("30.00"), "Cab", today));
            System.out.println("3: " + r3.alert());

            // clean up the test rows
            dao.delete(r1.expense().id(), 1);
            dao.delete(r2.expense().id(), 1);
            dao.delete(r3.expense().id(), 1);
        } catch (Exception e) {
        e.printStackTrace();
        }
    }
}
