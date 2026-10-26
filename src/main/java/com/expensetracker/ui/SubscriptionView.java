package com.expensetracker.ui;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.SubscriptionDao;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Frequency;
import com.expensetracker.model.Subscription;
import com.expensetracker.service.SubscriptionService;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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

public class SubscriptionView {
    private final SubscriptionDao subscriptionDao = new SubscriptionDao();
    private final SubscriptionService subscriptionService = new SubscriptionService();
    private final CategoryDao categoryDao = new CategoryDao();

    private final Map<Integer, String> categoryNames = new HashMap<>();
    private final TableView<Subscription> table = new TableView<>();
    private final TextField nameField = new TextField();
    private final TextField amountField = new TextField();
    private final ComboBox<Category> categoryBox = new ComboBox<>();
    private final ComboBox<Frequency> frequencyBox = new ComboBox<>();
    private final DatePicker dueDatePicker = new DatePicker(LocalDate.now());
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(12);

    public SubscriptionView() {
        buildForm();
        buildTable();
        loadCategories();
        refreshTable();
    }

    public Parent getRoot() {
        return root;
    }

    private void buildForm() {
        nameField.setPromptText("Name (e.g. Netflix)");
        amountField.setPromptText("Amount");
        categoryBox.setPromptText("Category");
        categoryBox.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.name(); }
            @Override public Category fromString(String s) { return null; }
        });
        frequencyBox.getItems().setAll(Frequency.values());
        frequencyBox.setValue(Frequency.MONTHLY);
        dueDatePicker.setPromptText("Next due");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button addButton = new Button("Add subscription");
        addButton.setOnAction(e -> onAdd());
        addButton.getStyleClass().add("primary");
        HBox form = new HBox(10, nameField, amountField, categoryBox, frequencyBox, dueDatePicker, addButton);

        Button processButton = new Button("Process due payments");
        processButton.setOnAction(e -> onProcess());
        Button deleteButton = new Button("Delete selected");
        deleteButton.setOnAction(e -> onDelete());
        HBox actions = new HBox(10, processButton, deleteButton);

        Label title = new Label("Subscriptions");
        title.getStyleClass().add("page-title");

        root.setPadding(new Insets(16));
        root.getChildren().addAll(title, form, actions, messageLabel, table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private void buildTable() {
        TableColumn<Subscription, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));

        TableColumn<Subscription, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(d -> new SimpleStringProperty(
                categoryNames.getOrDefault(d.getValue().categoryId(), "?")));

        TableColumn<Subscription, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().amount().toPlainString()));

        TableColumn<Subscription, String> freqCol = new TableColumn<>("Frequency");
        freqCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().frequency().name()));

        TableColumn<Subscription, String> dueCol = new TableColumn<>("Next due");
        dueCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nextDueDate().toString()));

        table.getColumns().addAll(nameCol, catCol, amountCol, freqCol, dueCol);
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
            table.getItems().setAll(subscriptionDao.findByUser(Session.userId()));
        } catch (SQLException e) {
            messageLabel.setText("Could not load subscriptions: " + e.getMessage());
        }
    }

    private void onAdd() {
        messageLabel.setText("");
        try {
            String name = nameField.getText().trim();
            Category category = categoryBox.getValue();
            LocalDate due = dueDatePicker.getValue();
            if (name.isEmpty()) { messageLabel.setText("Enter a name"); return; }
            if (category == null) { messageLabel.setText("Pick a category"); return; }
            if (due == null) { messageLabel.setText("Pick the next due date"); return; }

            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            if (amount.signum() <= 0) { messageLabel.setText("Amount must be greater than 0"); return; }

            subscriptionDao.add(new Subscription(0, Session.userId(), category.id(),
                    name, amount, frequencyBox.getValue(), due, true));
            nameField.clear();
            amountField.clear();
            refreshTable();
        } catch (NumberFormatException e) {
            messageLabel.setText("Enter a valid amount, e.g. 199.00");
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void onProcess() {
        try {
            List<Expense> created = subscriptionService.processDue(Session.userId(), LocalDate.now());
            refreshTable();
            messageLabel.setText(created.isEmpty()
                    ? "Nothing is due right now"
                    : "Added " + created.size() + " expense(s) from due subscriptions");
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void onDelete() {
        Subscription selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Select a subscription first");
            return;
        }
        try {
            subscriptionDao.delete(selected.id(), Session.userId());
            refreshTable();
            messageLabel.setText("Deleted " + selected.name());
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}