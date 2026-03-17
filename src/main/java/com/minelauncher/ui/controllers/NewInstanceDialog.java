package com.minelauncher.ui.controllers;

import com.minelauncher.fabric.FabricService;
import com.minelauncher.instances.Instance;
import com.minelauncher.minecraft.MinecraftVersionManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewInstanceDialog extends Dialog<Instance> {

    private static final Logger logger = LoggerFactory.getLogger(NewInstanceDialog.class);
    private final MinecraftVersionManager versionManager;
    private final FabricService fabricService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private TextField nameField;
    private ComboBox<String> versionCombo;
    private CheckBox useFabricCheck;
    private ComboBox<String> fabricVersionCombo;
    private Label statusLabel;
    private Button okButton;

    public NewInstanceDialog(MinecraftVersionManager versionManager,
                              FabricService fabricService, Stage owner) {
        this.versionManager = versionManager;
        this.fabricService = fabricService;

        setTitle("New Instance");
        setHeaderText("Create a new Minecraft instance");
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        getDialogPane().setPrefWidth(460);
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/launcher.css").toExternalForm());

        buildUI();
        setupResultConverter();
        loadVersions();
    }

    private void buildUI() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 20, 10, 20));

        // Name
        grid.add(new Label("Instance Name:"), 0, 0);
        nameField = new TextField("My Instance");
        nameField.setPromptText("Enter instance name");
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        grid.add(nameField, 1, 0);

        // MC Version
        grid.add(new Label("Minecraft Version:"), 0, 1);
        versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Loading versions...");
        versionCombo.setPrefWidth(200);
        grid.add(versionCombo, 1, 1);

        // Use Fabric
        useFabricCheck = new CheckBox("Use Fabric Mod Loader");
        useFabricCheck.setSelected(false);
        grid.add(useFabricCheck, 0, 2, 2, 1);

        // Fabric version
        grid.add(new Label("Fabric Version:"), 0, 3);
        fabricVersionCombo = new ComboBox<>();
        fabricVersionCombo.setPromptText("Select Minecraft version first");
        fabricVersionCombo.setPrefWidth(200);
        fabricVersionCombo.setDisable(true);
        grid.add(fabricVersionCombo, 1, 3);

        statusLabel = new Label("Loading versions...");
        statusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
        grid.add(statusLabel, 0, 4, 2, 1);

        // Toggle Fabric fields
        useFabricCheck.selectedProperty().addListener((obs, old, val) -> {
            fabricVersionCombo.setDisable(!val);
            if (val && versionCombo.getValue() != null) {
                loadFabricVersions(versionCombo.getValue());
            }
        });

        versionCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null && useFabricCheck.isSelected()) {
                loadFabricVersions(val);
            }
        });

        getDialogPane().setContent(grid);

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        okButton = (Button) getDialogPane().lookupButton(createBtn);
        okButton.setDisable(true);

        nameField.textProperty().addListener((obs, old, val) -> validateForm());
        versionCombo.valueProperty().addListener((obs, old, val) -> validateForm());
    }

    private void validateForm() {
        boolean valid = nameField.getText() != null && !nameField.getText().isBlank()
                && versionCombo.getValue() != null;
        if (useFabricCheck.isSelected()) {
            valid = valid && fabricVersionCombo.getValue() != null;
        }
        okButton.setDisable(!valid);
    }

    private void loadVersions() {
        executor.submit(() -> {
            try {
                List<MinecraftVersionManager.VersionInfo> versions =
                        versionManager.fetchVersionList();
                Platform.runLater(() -> {
                    for (MinecraftVersionManager.VersionInfo v : versions) {
                        if (v.isRelease()) versionCombo.getItems().add(v.id);
                        if (versionCombo.getItems().size() >= 50) break;
                    }
                    if (!versionCombo.getItems().isEmpty()) {
                        versionCombo.setValue(versionCombo.getItems().get(0));
                    }
                    statusLabel.setText("Versions loaded.");
                    validateForm();
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Failed to load versions: " + e.getMessage()));
            }
        });
    }

    private void loadFabricVersions(String mcVersion) {
        fabricVersionCombo.setDisable(true);
        fabricVersionCombo.getItems().clear();
        fabricVersionCombo.setPromptText("Loading...");
        executor.submit(() -> {
            try {
                List<FabricService.FabricLoaderVersion> versions =
                        fabricService.getLoaderVersions(mcVersion);
                Platform.runLater(() -> {
                    for (FabricService.FabricLoaderVersion v : versions) {
                        fabricVersionCombo.getItems().add(v.version);
                    }
                    if (!fabricVersionCombo.getItems().isEmpty()) {
                        fabricVersionCombo.setValue(fabricVersionCombo.getItems().get(0));
                    }
                    fabricVersionCombo.setDisable(false);
                    validateForm();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    fabricVersionCombo.setPromptText("Failed to load");
                    fabricVersionCombo.setDisable(false);
                });
            }
        });
    }

    private void setupResultConverter() {
        setResultConverter(button -> {
            if (button == null || button.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
            Instance inst = new Instance(nameField.getText().trim(), versionCombo.getValue());
            inst.useFabric = useFabricCheck.isSelected();
            if (inst.useFabric) {
                inst.fabricVersion = fabricVersionCombo.getValue();
            }
            return inst;
        });
    }
}
