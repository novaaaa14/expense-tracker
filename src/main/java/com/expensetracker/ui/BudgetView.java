package com.expensetracker.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class BudgetView {
    private final BudgetDao budgetDao = new BudgetDao();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final CategoryDao categoryDao = new CategoryDao();

    private final Map<Integer, String> categoryNames = new HashMap<>();
    private final ComboBox<Category> categoryBox = new ComboBox<>();
    private final TextField limitField = new TextField();
    private final TextField thresholdField = new TextField("80");
    private final Label messageLabel = new Label();
    private final VBox rowsBox = new VBox(14);
    private final VBox root = new VBox(12);

    public BudgetView() {
        buildForm();
        loadCategories();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    private void buildForm() {
        categoryBox.setPromptText("Category");
        categoryBox.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.name(); }
            @Override public Category fromString(String s) { return null; }
        });
        limitField.setPromptText("Monthly limit");
        thresholdField.setPromptText("Alert at %");
        thresholdField.setPrefWidth(90);

        Button saveButton = new Button("Save budget");
        saveButton.setOnAction(e -> onSave());
        HBox form = new HBox(10, categoryBox, limitField, new Label("Alert at"), thresholdField,
                new Label("%"), saveButton);
        form.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label title = new Label("Budgets (this month)");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        ScrollPane scroll = new ScrollPane(rowsBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.setPadding(new Insets(16));
        root.getChildren().addAll(title, form, messageLabel, scroll);
    }

    public void loadCategories() {
        try {
            var categories = categoryDao.findByUser(Session.userId());
            categoryNames.clear();
            categories.forEach(c -> categoryNames.put(c.id(), c.name()));
            categoryBox.getItems().setAll(categories);
        } catch (SQLException e) {
            messageLabel.setText("Could not load categories: " + e.getMessage());
        }
    }

    public void refresh() {
        rowsBox.getChildren().clear();
        YearMonth month = YearMonth.now();
        try {
            List<Budget> budgets = budgetDao.findByUser(Session.userId());
            if (budgets.isEmpty()) {
                rowsBox.getChildren().add(new Label("No budgets yet. Set one above."));
            }
            for (Budget b : budgets) {
                BigDecimal spent = expenseDao.totalForCategory(Session.userId(),
                        b.categoryId(), month.atDay(1), month.atEndOfMonth());
                rowsBox.getChildren().add(buildRow(b, spent));
            }
        } catch (SQLException e) {
            messageLabel.setText("Could not load budgets: " + e.getMessage());
        }
    }

    private VBox buildRow(Budget b, BigDecimal spent) {
        int percent = spent.multiply(BigDecimal.valueOf(100))
                           .divide(b.monthlyLimit(), 0, RoundingMode.DOWN)
                           .intValue();
        String color = spent.compareTo(b.monthlyLimit()) >= 0 ? "#e74c3c"          // exceeded
                     : percent >= b.alertThresholdPercent() ? "#f39c12"             // past threshold
                     : "#27ae60";                                                   // fine

        Label name = new Label(categoryNames.getOrDefault(b.categoryId(), "?"));
        name.setStyle("-fx-font-weight: bold;");
        Label amounts = new Label(String.format("%s / %s  (%d%%, alert at %d%%)",
                spent.toPlainString(), b.monthlyLimit().toPlainString(),
                percent, b.alertThresholdPercent()));

        ProgressBar bar = new ProgressBar(Math.min(1.0, spent.doubleValue() / b.monthlyLimit().doubleValue()));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-accent: " + color + ";");

        return new VBox(4, new HBox(10, name, amounts), bar);
    }

    private void onSave() {
        messageLabel.setText("");
        try {
            Category category = categoryBox.getValue();
            if (category == null) { messageLabel.setText("Pick a category"); return; }

            BigDecimal limit = new BigDecimal(limitField.getText().trim());
            int threshold = Integer.parseInt(thresholdField.getText().trim());
            if (limit.signum() <= 0) { messageLabel.setText("Limit must be greater than 0"); return; }
            if (threshold < 1 || threshold > 100) { messageLabel.setText("Alert % must be 1 to 100"); return; }

            budgetDao.save(new Budget(0, Session.userId(), category.id(), limit, threshold));
            limitField.clear();
            refresh();
            messageLabel.setText("Saved budget for " + category.name());
        } catch (NumberFormatException e) {
            messageLabel.setText("Limit and alert % must be numbers");
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}