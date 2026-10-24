package com.expensetracker.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class MainApp extends Application {
    public static final int CURRENT_USER_ID = 1;   // hardcoded until we add login

    @Override
    public void start(Stage stage) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ExpenseView expenseView = new ExpenseView();
        ChartView chartView = new ChartView();
        SubscriptionView subscriptionView = new SubscriptionView();

        Tab expensesTab = new Tab("Expenses", expenseView.getRoot());
        Tab chartsTab = new Tab("Charts", chartView.getRoot());
        Tab subscriptionsTab = new Tab("Subscriptions", subscriptionView.getRoot());

        // reload each tab's data when you switch to it
        expensesTab.setOnSelectionChanged(e -> { if (expensesTab.isSelected()) expenseView.refreshTable(); });
        chartsTab.setOnSelectionChanged(e -> { if (chartsTab.isSelected()) chartView.refresh(); });
        subscriptionsTab.setOnSelectionChanged(e -> { if (subscriptionsTab.isSelected()) subscriptionView.refreshTable(); });

        tabs.getTabs().addAll(expensesTab, chartsTab, subscriptionsTab);

        stage.setTitle("Expense Tracker");
        stage.setScene(new Scene(tabs, 1000, 600));
        stage.show();
    }
}