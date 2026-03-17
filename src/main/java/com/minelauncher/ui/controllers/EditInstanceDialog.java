package com.minelauncher.ui.controllers;

import com.minelauncher.instances.Instance;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public class EditInstanceDialog extends Dialog<Instance> {

    private final Instance instance;
    private TextField nameField;
    private TextField minMemField;
    private TextField maxMemField;
    private TextField jvmArgsField;

    public EditInstanceDialog(Instance instance, Stage owner) {
        this.instance = instance;

        setTitle("Edit Instance");
        setHeaderText("Edit: " + instance.name);
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        getDialogPane().setPrefWidth(440);
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/launcher.css").toExternalForm());

        buildUI();
        setupResultConverter();
    }

    private void buildUI() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0);
        nameField = new TextField(instance.name);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Min Memory (MB):"), 0, 1);
        minMemField = new TextField(String.valueOf(instance.minMemoryMb));
        grid.add(minMemField, 1, 1);

        grid.add(new Label("Max Memory (MB):"), 0, 2);
        maxMemField = new TextField(String.valueOf(instance.maxMemoryMb));
        grid.add(maxMemField, 1, 2);

        grid.add(new Label("Extra JVM Args:"), 0, 3);
        jvmArgsField = new TextField(instance.jvmArgs != null ? instance.jvmArgs : "");
        GridPane.setHgrow(jvmArgsField, Priority.ALWAYS);
        grid.add(jvmArgsField, 1, 3);

        grid.add(new Label("MC Version:"), 0, 4);
        grid.add(new Label(instance.minecraftVersion), 1, 4);

        if (instance.useFabric) {
            grid.add(new Label("Fabric:"), 0, 5);
            grid.add(new Label(instance.fabricVersion != null ? instance.fabricVersion : "N/A"), 1, 5);
        }

        getDialogPane().setContent(grid);
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
    }

    private void setupResultConverter() {
        setResultConverter(btn -> {
            if (btn == null || btn.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
            instance.name = nameField.getText().trim();
            try { instance.minMemoryMb = Integer.parseInt(minMemField.getText().trim()); } catch (NumberFormatException ignored) {}
            try { instance.maxMemoryMb = Integer.parseInt(maxMemField.getText().trim()); } catch (NumberFormatException ignored) {}
            instance.jvmArgs = jvmArgsField.getText().trim();
            return instance;
        });
    }
}
