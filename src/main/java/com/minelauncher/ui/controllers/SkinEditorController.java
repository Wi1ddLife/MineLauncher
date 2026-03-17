package com.minelauncher.ui.controllers;

import com.minelauncher.auth.MicrosoftAuthService;
import com.minelauncher.config.LauncherConfig;
import com.minelauncher.skin.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;

/**
 * Full skin + cape editor panel.
 * Returns a VBox that can be embedded anywhere in the main UI.
 */
public class SkinEditorController {

    private static final Logger logger = LoggerFactory.getLogger(SkinEditorController.class);

    private final SkinService skinService = new SkinService();
    private final MicrosoftAuthService authService = new MicrosoftAuthService();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r); t.setDaemon(true); return t;
    });

    private VBox skinsGrid;
    private VBox capesGrid;
    private ImageView skinPreview;
    private Label activeLabel;
    private Label activeCapeLabel;
    private Label statusLabel;
    private Stage ownerStage;

    public VBox buildPane(Stage owner) {
        this.ownerStage = owner;

        VBox root = new VBox(0);
        root.getStyleClass().add("skin-editor-pane");

        // ── Header ────────────────────────────────────────────────────────────
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 28, 16, 28));
        header.setStyle("-fx-background-color:#0f1117; -fx-border-color:#1e2130; -fx-border-width:0 0 1 0;");

        VBox headerText = new VBox(4);
        Label title = new Label("Skin & Cape Editor");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Manage your Minecraft skins and cosmetic capes");
        subtitle.setStyle("-fx-text-fill:#4a5578; -fx-font-size:12px;");
        headerText.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Client branding badges
        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_RIGHT);
        Label mineBadge    = new Label("⛏ MineLauncher");
        Label lunarBadge   = new Label("🌙 Lunar");
        Label featherBadge = new Label("🪶 Feather");
        mineBadge.getStyleClass().addAll("client-badge", "client-badge-mine");
        lunarBadge.getStyleClass().addAll("client-badge", "client-badge-lunar");
        featherBadge.getStyleClass().addAll("client-badge", "client-badge-feather");
        badges.getChildren().addAll(mineBadge, lunarBadge, featherBadge);

        header.getChildren().addAll(headerText, spacer, badges);

        // ── Status bar ────────────────────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill:#4d7cfe; -fx-font-size:12px; -fx-padding:6 28;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Main content ──────────────────────────────────────────────────────
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.3);
        split.setStyle("-fx-background-color:#0a0b0e;");
        VBox.setVgrow(split, Priority.ALWAYS);

        split.getItems().addAll(buildPreviewPanel(), buildEditorPanel());

        root.getChildren().addAll(header, statusLabel, split);
        return root;
    }

    // ── Left: 3D-style skin preview ───────────────────────────────────────────

    private VBox buildPreviewPanel() {
        VBox panel = new VBox(16);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(24));
        panel.setStyle("-fx-background-color:#0a0b0e;");

        Label previewTitle = new Label("Preview");
        previewTitle.setStyle("-fx-text-fill:#4a5578; -fx-font-size:11px; -fx-font-weight:700; -fx-letter-spacing:1.5px;");

        // Skin preview using Crafatar
        VBox previewBox = new VBox(0);
        previewBox.setAlignment(Pos.CENTER);
        previewBox.getStyleClass().add("skin-preview-box");
        previewBox.setPrefWidth(180);

        skinPreview = new ImageView();
        skinPreview.setFitWidth(130);
        skinPreview.setFitHeight(260);
        skinPreview.setPreserveRatio(true);
        skinPreview.setSmooth(true);
        skinPreview.setStyle("-fx-effect: dropshadow(gaussian, rgba(77,124,254,0.3), 20, 0, 0, 8);");

        // Load current skin from Crafatar
        loadSkinPreview();

        activeLabel = new Label("No skin selected");
        activeLabel.setStyle("-fx-text-fill:#4a5578; -fx-font-size:11px;");

        previewBox.getChildren().addAll(skinPreview, new Region() {{ setPrefHeight(12); }}, activeLabel);

        // Active cape display
        VBox capeBox = new VBox(8);
        capeBox.setAlignment(Pos.CENTER);
        capeBox.setStyle("-fx-background-color:#0f1117; -fx-background-radius:12; -fx-border-color:#1e2130; -fx-border-radius:12; -fx-border-width:1; -fx-padding:14;");

        Label capeTitle = new Label("Active Cape");
        capeTitle.setStyle("-fx-text-fill:#4a5578; -fx-font-size:10px; -fx-font-weight:700; -fx-letter-spacing:1px;");

        activeCapeLabel = new Label("None");
        activeCapeLabel.setStyle("-fx-text-fill:#8892b0; -fx-font-size:12px; -fx-font-weight:600;");

        Button removeCapeBtn = new Button("Remove Cape");
        removeCapeBtn.getStyleClass().add("btn-secondary");
        removeCapeBtn.setStyle("-fx-font-size:11px; -fx-padding:5 12;");
        removeCapeBtn.setOnAction(e -> handleRemoveCape());

        capeBox.getChildren().addAll(capeTitle, activeCapeLabel, removeCapeBtn);

        // Update active labels
        refreshActiveLabels();

        panel.getChildren().addAll(previewTitle, previewBox, capeBox);
        return panel;
    }

    // ── Right: tabs for Skins / Capes ─────────────────────────────────────────

    private TabPane buildEditorPanel() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:#0a0b0e;");

        tabs.getTabs().addAll(buildSkinsTab(), buildCapesTab());
        return tabs;
    }

    private Tab buildSkinsTab() {
        Tab tab = new Tab("🎨  Skins");

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:#0a0b0e;");

        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(16, 20, 12, 20));
        toolbar.setStyle("-fx-background-color:#0f1117; -fx-border-color:#1e2130; -fx-border-width:0 0 1 0;");

        Button addSkinBtn = new Button("+ Add Skin");
        addSkinBtn.getStyleClass().add("btn-primary");
        addSkinBtn.setOnAction(e -> handleAddSkin());

        Label variantHint = new Label("PNG files, 64×64 or 64×32 pixels");
        variantHint.setStyle("-fx-text-fill:#2d3554; -fx-font-size:11px;");

        toolbar.getChildren().addAll(addSkinBtn, variantHint);

        // Skins grid
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent; -fx-background:#0a0b0e;");

        skinsGrid = new VBox(8);
        skinsGrid.setPadding(new Insets(16, 20, 20, 20));
        skinsGrid.setStyle("-fx-background-color:#0a0b0e;");
        scroll.setContent(skinsGrid);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        content.getChildren().addAll(toolbar, scroll);
        refreshSkinsGrid();
        tab.setContent(content);
        return tab;
    }

    private Tab buildCapesTab() {
        Tab tab = new Tab("🦸  Capes");

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:#0a0b0e;");

        // Source filter
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(16, 20, 12, 20));
        toolbar.setStyle("-fx-background-color:#0f1117; -fx-border-color:#1e2130; -fx-border-width:0 0 1 0;");

        Label filterLabel = new Label("Show:");
        filterLabel.setStyle("-fx-text-fill:#4a5578; -fx-font-size:12px;");

        ToggleGroup group = new ToggleGroup();
        ToggleButton allBtn     = filterToggle("All",     "ALL",     group);
        ToggleButton lunarBtn   = filterToggle("🌙 Lunar", "LUNAR",  group);
        ToggleButton featherBtn = filterToggle("🪶 Feather","FEATHER",group);
        ToggleButton customBtn  = filterToggle("Custom",  "CUSTOM",  group);
        allBtn.setSelected(true);

        Button addCustomCapeBtn = new Button("+ Custom Cape");
        addCustomCapeBtn.getStyleClass().add("btn-secondary");
        addCustomCapeBtn.setOnAction(e -> handleAddCustomCape());
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        toolbar.getChildren().addAll(filterLabel, allBtn, lunarBtn, featherBtn, customBtn, sp, addCustomCapeBtn);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:transparent; -fx-background:#0a0b0e;");

        capesGrid = new VBox(8);
        capesGrid.setPadding(new Insets(16, 20, 20, 20));
        capesGrid.setStyle("-fx-background-color:#0a0b0e;");
        scroll.setContent(capesGrid);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Filter logic
        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n instanceof ToggleButton tb) {
                refreshCapesGrid((String) tb.getUserData());
            }
        });

        content.getChildren().addAll(toolbar, scroll);
        refreshCapesGrid("ALL");
        tab.setContent(content);
        return tab;
    }

    private ToggleButton filterToggle(String label, String data, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(label);
        btn.setToggleGroup(group);
        btn.setUserData(data);
        btn.setStyle("-fx-background-color:#161a27; -fx-text-fill:#4a5578; -fx-background-radius:8;" +
                "-fx-border-color:#1e2130; -fx-border-radius:8; -fx-border-width:1;" +
                "-fx-padding:5 12; -fx-cursor:hand; -fx-font-size:12px;");
        btn.selectedProperty().addListener((obs, o, sel) -> btn.setStyle(
                (sel ? "-fx-background-color:#0d1829; -fx-text-fill:#4d7cfe; -fx-border-color:#2d4a8a;"
                     : "-fx-background-color:#161a27; -fx-text-fill:#4a5578; -fx-border-color:#1e2130;")
                + "-fx-background-radius:8; -fx-border-radius:8; -fx-border-width:1; -fx-padding:5 12; -fx-cursor:hand; -fx-font-size:12px;"));
        return btn;
    }

    // ── Grid builders ─────────────────────────────────────────────────────────

    private void refreshSkinsGrid() {
        skinsGrid.getChildren().clear();
        List<SkinModel> skins = skinService.getSkins();

        if (skins.isEmpty()) {
            Label empty = new Label("No skins added yet. Click '+ Add Skin' to upload a PNG.");
            empty.setStyle("-fx-text-fill:#2d3554; -fx-font-size:13px; -fx-padding:20;");
            skinsGrid.getChildren().add(empty);
            return;
        }

        for (SkinModel skin : skins) {
            HBox card = buildSkinCard(skin);
            skinsGrid.getChildren().add(card);
        }
    }

    private HBox buildSkinCard(SkinModel skin) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("skin-card");
        if (skin.isActive) card.getStyleClass().add("selected");

        // Skin avatar preview
        ImageView avatar = new ImageView();
        avatar.setFitWidth(40); avatar.setFitHeight(40);
        avatar.setPreserveRatio(true);
        if (skin.localPath != null) {
            try {
                Image img = new Image("file:///" + skin.localPath.replace("\\", "/"), 40, 40, true, true, true);
                avatar.setImage(img);
            } catch (Exception ignored) {}
        }

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLabel = new Label(skin.name);
        nameLabel.getStyleClass().add("skin-card-name");
        Label typeLabel = new Label(skin.variant + (skin.isActive ? "  •  Active" : ""));
        typeLabel.getStyleClass().add("skin-card-type");
        if (skin.isActive) typeLabel.setStyle("-fx-text-fill:#4d7cfe; -fx-font-size:10px;");
        info.getChildren().addAll(nameLabel, typeLabel);

        // Action buttons
        Button applyBtn = new Button(skin.isActive ? "✓ Applied" : "Apply");
        applyBtn.getStyleClass().add(skin.isActive ? "btn-success" : "btn-primary");
        applyBtn.setStyle("-fx-font-size:11px; -fx-padding:5 12;");
        applyBtn.setDisable(skin.isActive);
        applyBtn.setOnAction(e -> handleApplySkin(skin));

        Button deleteBtn = new Button("✕");
        deleteBtn.getStyleClass().add("btn-icon-danger");
        deleteBtn.setOnAction(e -> handleDeleteSkin(skin));
        deleteBtn.setTooltip(new Tooltip("Remove skin"));

        card.getChildren().addAll(avatar, info, applyBtn, deleteBtn);
        return card;
    }

    private void refreshCapesGrid(String filter) {
        capesGrid.getChildren().clear();
        List<CapeModel> capes = skinService.getCapes();

        List<CapeModel> filtered = capes.stream()
                .filter(c -> filter.equals("ALL") || filter.equals(c.source))
                .toList();

        if (filtered.isEmpty()) {
            Label empty = new Label("No capes in this category.");
            empty.setStyle("-fx-text-fill:#2d3554; -fx-font-size:13px; -fx-padding:20;");
            capesGrid.getChildren().add(empty);
            return;
        }

        // Group by source with headers
        String lastSource = "";
        for (CapeModel cape : filtered) {
            if (filter.equals("ALL") && !cape.source.equals(lastSource)) {
                Label sourceHeader = new Label(sourceLabel(cape.source));
                sourceHeader.setStyle("-fx-text-fill:#4a5578; -fx-font-size:10px; -fx-font-weight:700;" +
                        "-fx-letter-spacing:1.5px; -fx-padding:8 0 4 0;");
                capesGrid.getChildren().add(sourceHeader);
                lastSource = cape.source;
            }
            capesGrid.getChildren().add(buildCapeCard(cape));
        }
    }

    private HBox buildCapeCard(CapeModel cape) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.getStyleClass().add("cape-card");
        if (cape.isActive) card.getStyleClass().add("selected");

        // Cape icon from URL or local
        ImageView icon = new ImageView();
        icon.setFitWidth(36); icon.setFitHeight(36);
        icon.setPreserveRatio(true);
        if (cape.previewUrl != null) {
            try {
                Image img = new Image(cape.previewUrl, 36, 36, true, true, true);
                icon.setImage(img);
            } catch (Exception ignored) {}
        } else if (cape.localPath != null) {
            try {
                Image img = new Image("file:///" + cape.localPath.replace("\\", "/"), 36, 36, true, true, true);
                icon.setImage(img);
            } catch (Exception ignored) {}
        }

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLabel = new Label(cape.name);
        nameLabel.getStyleClass().add("cape-card-name");
        Label sourceLabel = new Label(sourceLabel(cape.source) + (cape.isActive ? "  •  Active" : ""));
        sourceLabel.getStyleClass().add("cape-card-source");
        if (cape.isActive) sourceLabel.setStyle("-fx-text-fill:#a855f7; -fx-font-size:10px;");
        info.getChildren().addAll(nameLabel, sourceLabel);

        Button applyBtn = new Button(cape.isActive ? "✓ Active" : "Equip");
        applyBtn.getStyleClass().add(cape.isActive ? "btn-success" : "btn-primary");
        applyBtn.setStyle("-fx-font-size:11px; -fx-padding:5 12;");
        applyBtn.setDisable(cape.isActive);
        applyBtn.setOnAction(e -> handleApplyCape(cape));

        card.getChildren().addAll(icon, info, applyBtn);
        return card;
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleAddSkin() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose Skin PNG");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = fc.showOpenDialog(ownerStage);
        if (file == null) return;

        // Ask for name + variant
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Add Skin");
        dialog.setHeaderText("Configure your skin");
        dialog.initOwner(ownerStage);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField(file.getName().replace(".png", ""));
        nameField.setPromptText("Skin name");

        ComboBox<String> variantCombo = new ComboBox<>();
        variantCombo.getItems().addAll("CLASSIC", "SLIM");
        variantCombo.setValue("CLASSIC");
        variantCombo.setTooltip(new Tooltip("CLASSIC = Steve model, SLIM = Alex model"));

        grid.add(new Label("Name:"), 0, 0);    grid.add(nameField, 1, 0);
        grid.add(new Label("Model:"), 0, 1);   grid.add(variantCombo, 1, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new String[]{nameField.getText().trim(), variantCombo.getValue()}
                : null);

        dialog.showAndWait().ifPresent(result -> {
            if (result == null || result[0].isBlank()) return;
            try {
                SkinModel skin = skinService.addSkin(result[0], file.toPath(), result[1]);
                setStatus("Skin '" + skin.name + "' added!");
                refreshSkinsGrid();
            } catch (Exception e) {
                setStatus("Failed to add skin: " + e.getMessage(), true);
            }
        });
    }

    private void handleApplySkin(SkinModel skin) {
        String token = LauncherConfig.getInstance().getData().auth.accessToken;
        if (token == null || token.isBlank()) {
            setStatus("Please log in first to apply skins.", true);
            return;
        }
        setStatus("Uploading skin...");
        executor.submit(() -> {
            try {
                skinService.applySkin(skin, token);
                Platform.runLater(() -> {
                    setStatus("Skin applied! It will show in-game shortly.");
                    loadSkinPreview();
                    refreshSkinsGrid();
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Failed to apply skin: " + e.getMessage(), true));
            }
        });
    }

    private void handleDeleteSkin(SkinModel skin) {
        skinService.removeSkin(skin);
        setStatus("Skin removed.");
        refreshSkinsGrid();
    }

    private void handleApplyCape(CapeModel cape) {
        String token = LauncherConfig.getInstance().getData().auth.accessToken;
        if ("MOJANG".equals(cape.source) && (token == null || token.isBlank())) {
            setStatus("Please log in first.", true);
            return;
        }
        executor.submit(() -> {
            try {
                skinService.applyCape(cape, token != null ? token : "");
                Platform.runLater(() -> {
                    String msg = switch (cape.source) {
                        case "LUNAR"   -> "🌙 Lunar cape '" + cape.name + "' equipped! (cosmetic)";
                        case "FEATHER" -> "🪶 Feather cape '" + cape.name + "' equipped! (cosmetic)";
                        default        -> "Cape '" + cape.name + "' applied!";
                    };
                    setStatus(msg);
                    refreshCapesGrid("ALL");
                    refreshActiveLabels();
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Failed: " + e.getMessage(), true));
            }
        });
    }

    private void handleRemoveCape() {
        String token = LauncherConfig.getInstance().getData().auth.accessToken;
        executor.submit(() -> {
            try {
                if (token != null && !token.isBlank()) skinService.removeCape(token);
                else skinService.getCapes().forEach(c -> c.isActive = false);
                Platform.runLater(() -> {
                    setStatus("Cape removed.");
                    refreshCapesGrid("ALL");
                    refreshActiveLabels();
                });
            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Failed: " + e.getMessage(), true));
            }
        });
    }

    private void handleAddCustomCape() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose Cape PNG (64×32 pixels)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = fc.showOpenDialog(ownerStage);
        if (file == null) return;

        TextInputDialog nameDialog = new TextInputDialog(file.getName().replace(".png", ""));
        nameDialog.setTitle("Cape Name");
        nameDialog.setHeaderText("Enter a name for this cape:");
        nameDialog.initOwner(ownerStage);
        nameDialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            try {
                CapeModel cape = skinService.addCustomCape(name, file.toPath());
                setStatus("Custom cape '" + cape.name + "' added!");
                refreshCapesGrid("ALL");
            } catch (Exception e) {
                setStatus("Failed to add cape: " + e.getMessage(), true);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadSkinPreview() {
        String uuid = LauncherConfig.getInstance().getData().auth.uuid;
        if (uuid == null || uuid.isBlank()) {
            skinPreview.setImage(null);
            return;
        }
        executor.submit(() -> {
            try {
                // Use Crafatar full body render
                String url = "https://crafatar.com/renders/body/" + uuid + "?size=260&overlay=true";
                Image img = new Image(url, 130, 260, true, true, true);
                Platform.runLater(() -> skinPreview.setImage(img));
            } catch (Exception e) {
                logger.debug("Could not load skin preview", e);
            }
        });
    }

    private void refreshActiveLabels() {
        SkinModel activeSkin = skinService.getActiveSkin();
        CapeModel activeCape = skinService.getActiveCape();

        activeLabel.setText(activeSkin != null ? activeSkin.name : "Current account skin");
        activeCapeLabel.setText(activeCape != null
                ? sourceLabel(activeCape.source) + ": " + activeCape.name
                : "None");
    }

    private void setStatus(String msg) { setStatus(msg, false); }
    private void setStatus(String msg, boolean error) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle((error ? "-fx-text-fill:#f56565;" : "-fx-text-fill:#4d7cfe;")
                    + "-fx-font-size:12px; -fx-padding:6 28;");
        });
    }

    private String sourceLabel(String source) {
        return switch (source) {
            case "LUNAR"   -> "🌙 Lunar Client";
            case "FEATHER" -> "🪶 Feather Client";
            case "MOJANG"  -> "Mojang";
            default        -> "Custom";
        };
    }
}
