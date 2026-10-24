package com.expensetracker.ui;

import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;

import com.expensetracker.model.User;
import com.expensetracker.service.AuthService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginView {
    private final AuthService authService = new AuthService();
    private final Consumer<User> onSuccess;

    private final Label titleLabel = new Label();
    private final TextField nameField = new TextField();
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button submitButton = new Button();
    private final Hyperlink toggleLink = new Hyperlink();
    private final Label messageLabel = new Label();
    private final VBox root = new VBox();

    private boolean registerMode = false;

    public LoginView(Consumer<User> onSuccess) {
        this.onSuccess = onSuccess;

        titleLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        nameField.setPromptText("Name");
        emailField.setPromptText("Email");
        passwordField.setPromptText("Password");
        messageLabel.setStyle("-fx-text-fill: #c0392b;");
        messageLabel.setWrapText(true);
        submitButton.setMaxWidth(Double.MAX_VALUE);

        submitButton.setOnAction(e -> submit());
        passwordField.setOnAction(e -> submit());      // Enter key submits
        toggleLink.setOnAction(e -> setMode(!registerMode));

        VBox form = new VBox(12, titleLabel, nameField, emailField, passwordField,
                submitButton, messageLabel, toggleLink);
        form.setMaxWidth(340);

        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getChildren().add(form);

        setMode(false);
    }

    public Parent getRoot() {
        return root;
    }

    private void setMode(boolean register) {
        registerMode = register;
        titleLabel.setText(register ? "Create account" : "Log in");
        submitButton.setText(register ? "Create account" : "Log in");
        toggleLink.setText(register ? "Already have an account? Log in" : "New here? Create an account");
        nameField.setVisible(register);
        nameField.setManaged(register);       // managed=false removes it from the layout
        messageLabel.setText("");
    }

    private void submit() {
        messageLabel.setText("");
        try {
            User user;
            if (registerMode) {
                user = authService.register(nameField.getText(), emailField.getText(), passwordField.getText());
            } else {
                Optional<User> found = authService.login(emailField.getText(), passwordField.getText());
                if (found.isEmpty()) {
                    messageLabel.setText("Invalid email or password");
                    return;
                }
                user = found.get();
            }
            passwordField.clear();
            onSuccess.accept(user);
        } catch (IllegalArgumentException e) {
            messageLabel.setText(e.getMessage());     // validation errors from the service
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}