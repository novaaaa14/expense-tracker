package com.expensetracker.ui;

import java.sql.SQLException;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.model.Category;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CategoryView {
    private final CategoryDao categoryDao = new CategoryDao();
    private final ListView<Category> list = new ListView<>();
    private final TextField nameField = new TextField();
    private final Label messageLabel = new Label();
    private final VBox root = new VBox(12);

    public CategoryView() {
        Label title = new Label("Categories");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        nameField.setPromptText("New category name");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        Button addButton = new Button("Add category");
        addButton.setOnAction(e -> onAdd());
        HBox form = new HBox(10, nameField, addButton);

        Button deleteButton = new Button("Delete selected");
        deleteButton.setOnAction(e -> onDelete());

        // show just the name instead of the record's default toString
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        root.setPadding(new Insets(16));
        root.getChildren().addAll(title, form, deleteButton, messageLabel, list);
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    public void refresh() {
        try {
            list.getItems().setAll(categoryDao.findByUser(Session.userId()));
        } catch (SQLException e) {
            messageLabel.setText("Could not load categories: " + e.getMessage());
        }
    }

    private void onAdd() {
        messageLabel.setText("");
        String name = nameField.getText().trim();
        if (name.isEmpty()) { messageLabel.setText("Enter a name"); return; }
        if (name.length() > 50) { messageLabel.setText("Name can be at most 50 characters"); return; }

        try {
            categoryDao.add(new Category(0, Session.userId(), name));
            nameField.clear();
            refresh();
            messageLabel.setText("Added " + name);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {          // unique constraint violated
                messageLabel.setText("You already have a category called " + name);
            } else {
                messageLabel.setText("Database error: " + e.getMessage());
            }
        }
    }

    private void onDelete() {
        Category selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) { messageLabel.setText("Select a category first"); return; }

        try {
            categoryDao.delete(selected.id(), Session.userId());
            refresh();
            messageLabel.setText("Deleted " + selected.name());
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {          // foreign key violated
                messageLabel.setText("Can't delete " + selected.name()
                        + ": it still has expenses or subscriptions");
            } else {
                messageLabel.setText("Database error: " + e.getMessage());
            }
        }
    }
}