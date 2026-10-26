package com.expensetracker.ui;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.AddExpenseResult;
import com.expensetracker.model.BudgetAlert;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

public class ExpenseView {
    private final ExpenseService expenseService = new ExpenseService();
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final CategoryDao categoryDao = new CategoryDao();

    private final Map<Integer, String> categoryNames = new HashMap<>();
    private final TableView<Expense> table = new TableView<>();
    private final ComboBox<Category> categoryBox = new ComboBox<>();
    private final TextField amountField = new TextField();
    private final TextField descField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(12);

    public ExpenseView() {
        buildForm();
        buildTable();
        loadCategories();
        refreshTable();
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
        amountField.setPromptText("Amount");
        descField.setPromptText("Description");
        HBox.setHgrow(descField, Priority.ALWAYS);

        Button addButton = new Button("Add expense");
        addButton.setOnAction(e -> onAdd());
        addButton.getStyleClass().add("primary");

        HBox form = new HBox(10, categoryBox, amountField, descField, datePicker, addButton);

        Label title = new Label("Expenses");
        title.getStyleClass().add("page-title");

        root.setPadding(new Insets(16));
        root.getChildren().addAll(title, form, messageLabel, table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private void buildTable() {
        TableColumn<Expense, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().expenseDate().toString()));

        TableColumn<Expense, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(d -> new SimpleStringProperty(
                categoryNames.getOrDefault(d.getValue().categoryId(), "?")));

        TableColumn<Expense, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().description()));

        TableColumn<Expense, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amount().toPlainString()));

        table.getColumns().addAll(dateCol, catCol, descCol, amountCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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

    public void refreshTable() {
        try {
            table.getItems().setAll(expenseDao.findByUser(Session.userId()));
        } catch (SQLException e) {
            messageLabel.setText("Could not load expenses: " + e.getMessage());
        }
    }

    private void onAdd() {
        messageLabel.setText("");
        try {
            Category category = categoryBox.getValue();
            if (category == null) {
                messageLabel.setText("Pick a category");
                return;
            }
            BigDecimal amount = new BigDecimal(amountField.getText().trim());

            AddExpenseResult result = expenseService.addExpense(new Expense(
                    0, Session.userId(), category.id(), amount,
                    descField.getText().trim(), datePicker.getValue()));

            amountField.clear();
            descField.clear();
            refreshTable();
            result.alert().ifPresent(this::showAlert);
        } catch (NumberFormatException e) {
            messageLabel.setText("Enter a valid amount, e.g. 250.00");
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());   // validation errors from the service
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void showAlert(BudgetAlert a) {
        String category = categoryNames.getOrDefault(a.categoryId(), "this category");
        boolean exceeded = a.status() == BudgetAlert.Status.EXCEEDED;

        String text = String.format("%s: %s of %s spent this month (%d%% of budget).",
                category, a.spent().toPlainString(), a.limit().toPlainString(), a.percentUsed());

        Alert alert = new Alert(exceeded ? Alert.AlertType.ERROR : Alert.AlertType.WARNING,
                text, ButtonType.OK);
        alert.setHeaderText(exceeded ? "Budget exceeded!" : "Approaching your budget");
        alert.show();
    }
}