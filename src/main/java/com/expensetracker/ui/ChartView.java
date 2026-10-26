package com.expensetracker.ui;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.ExpenseDao;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ChartView {
    private final ExpenseDao expenseDao = new ExpenseDao();
    private final CategoryDao categoryDao = new CategoryDao();

    private final PieChart pie = new PieChart();
    private final Label monthLabel = new Label();
    private final Label totalLabel = new Label();
    private final Label detailsTitle = new Label();
    private final ListView<String> detailsList = new ListView<>();
    private final VBox root = new VBox(12);

    private YearMonth month = YearMonth.now();

    public ChartView() {
        Label title = new Label("Spending by category");
        title.getStyleClass().add("page-title");

        Button prev = new Button("◀");
        Button next = new Button("▶");
        prev.setOnAction(e -> { month = month.minusMonths(1); refresh(); });
        next.setOnAction(e -> { month = month.plusMonths(1); refresh(); });
        monthLabel.getStyleClass().add("month-label");        
        HBox nav = new HBox(10, prev, monthLabel, next);
        nav.setAlignment(Pos.CENTER_LEFT);

        pie.setLegendSide(Side.RIGHT);
        pie.setLabelsVisible(false);
        detailsList.setPrefHeight(150);
        VBox.setVgrow(pie, Priority.ALWAYS);

        root.setPadding(new Insets(16));
        root.getChildren().addAll(title, nav, totalLabel, pie, detailsTitle, detailsList);
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    public void refresh() {
        monthLabel.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        detailsTitle.setText("Click a slice to see its expenses");
        detailsList.getItems().clear();

        try {
            Map<String, BigDecimal> totals = expenseDao.totalsByCategory(
                    Session.userId(), month.atDay(1), month.atEndOfMonth());
            BigDecimal grand = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            totalLabel.setText(totals.isEmpty() ? "No expenses this month"
                                                : "Total spent: " + grand.toPlainString());

            // the legend shows "Food (39%)", but we keep the plain names for the click lookup
            List<String> names = new ArrayList<>(totals.keySet());
            List<PieChart.Data> slices = new ArrayList<>();
            for (String name : names) {
                double amount = totals.get(name).doubleValue();
                double percent = amount / grand.doubleValue() * 100;
                slices.add(new PieChart.Data(String.format("%s (%.0f%%)", name, percent), amount));
        }
            pie.setData(FXCollections.observableArrayList(slices));

            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                PieChart.Data slice = pie.getData().get(i);
                Node node = slice.getNode();

                Tooltip.install(node, new Tooltip(String.format("%s: %.2f", name, slice.getPieValue())));
                node.setCursor(Cursor.HAND);
                node.setOnMouseEntered(e -> { node.setScaleX(1.05); node.setScaleY(1.05); });
                node.setOnMouseExited(e -> { node.setScaleX(1); node.setScaleY(1); });
                node.setOnMouseClicked(e -> showDetails(name));
            }
        } catch (SQLException e) {
            totalLabel.setText("Could not load chart: " + e.getMessage());
        }
    }

    private void showDetails(String categoryName) {
        try {
            Map<Integer, String> names = new HashMap<>();
            categoryDao.findByUser(Session.userId()).forEach(c -> names.put(c.id(), c.name()));
            LocalDate from = month.atDay(1);
            LocalDate to = month.atEndOfMonth();

            detailsTitle.setText(categoryName + " expenses in " + monthLabel.getText());
            detailsList.getItems().setAll(
                    expenseDao.findByUser(Session.userId()).stream()
                            .filter(x -> categoryName.equals(names.get(x.categoryId())))
                            .filter(x -> !x.expenseDate().isBefore(from) && !x.expenseDate().isAfter(to))
                            .map(x -> String.format("%s   %s   %s", x.expenseDate(),
                                    x.description() == null ? "" : x.description(),
                                    x.amount().toPlainString()))
                            .toList());
        } catch (SQLException ex) {
            detailsTitle.setText("Could not load details: " + ex.getMessage());
        }
    }
}