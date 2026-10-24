package com.expensetracker.ui;

import java.sql.SQLException;
import java.time.LocalDate;

import com.expensetracker.model.User;
import com.expensetracker.service.SubscriptionService;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class MainApp extends Application {
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new Region(), 1000, 650);
        stage.setTitle("Expense Tracker");
        stage.setScene(scene);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        scene.setRoot(new LoginView(this::onLogin).getRoot());
    }

    private void onLogin(User user) {
        Session.login(user);
        showMain();
    }

    private void showMain() {
        // create expenses for any subscriptions that came due while the app was closed
        try {
            new SubscriptionService().processDue(Session.userId(), LocalDate.now());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ExpenseView expenseView = new ExpenseView();
        ChartView chartView = new ChartView();
        BudgetView budgetView = new BudgetView();
        SubscriptionView subscriptionView = new SubscriptionView();
        CategoryView categoryView = new CategoryView();

        Tab expensesTab = new Tab("Expenses", expenseView.getRoot());
        Tab chartsTab = new Tab("Charts", chartView.getRoot());
        Tab budgetsTab = new Tab("Budgets", budgetView.getRoot());
        Tab subscriptionsTab = new Tab("Subscriptions", subscriptionView.getRoot());
        Tab categoriesTab = new Tab("Categories", categoryView.getRoot());

        expensesTab.setOnSelectionChanged(e -> {
            if (expensesTab.isSelected()) { expenseView.loadCategories(); expenseView.refreshTable(); }
        });
        chartsTab.setOnSelectionChanged(e -> {
            if (chartsTab.isSelected()) chartView.refresh();
        });
        budgetsTab.setOnSelectionChanged(e -> {
            if (budgetsTab.isSelected()) { budgetView.loadCategories(); budgetView.refresh(); }
        });
        subscriptionsTab.setOnSelectionChanged(e -> {
            if (subscriptionsTab.isSelected()) { subscriptionView.loadCategories(); subscriptionView.refreshTable(); }
        });
        categoriesTab.setOnSelectionChanged(e -> {
            if (categoriesTab.isSelected()) categoryView.refresh();
        });

        tabs.getTabs().addAll(expensesTab, chartsTab, budgetsTab, subscriptionsTab, categoriesTab);

        Label who = new Label("Signed in as " + Session.user().name());
        Button logout = new Button("Log out");
        logout.setOnAction(e -> {
            Session.logout();
            showLogin();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(10, who, spacer, logout);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(8, 16, 8, 16));

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);
        layout.setCenter(tabs);
        scene.setRoot(layout);
    }
}