package com.minelauncher.ui.controllers;

import com.minelauncher.auth.MicrosoftAuthService;
import com.minelauncher.config.LauncherConfig;
import com.minelauncher.fabric.FabricService;
import com.minelauncher.instances.Instance;
import com.minelauncher.instances.InstanceManager;
import com.minelauncher.minecraft.GameLauncher;
import com.minelauncher.minecraft.MinecraftVersionManager;
import com.minelauncher.modrinth.ModrinthService;
import com.minelauncher.ui.components.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Top bar
    @FXML private HBox userInfoBox;
    @FXML private ImageView avatarImage;
    @FXML private Label usernameLabel;
    @FXML private Button loginButton;
    @FXML private Button logoutButton;

    // Sidebar
    @FXML private VBox instanceListContainer;

    // Tabs
    @FXML private TabPane mainTabPane;
    @FXML private Tab instanceTab, modsTab, skinsTab, settingsTab, logTab;

    // Instance pane
    @FXML private VBox noInstancePane, instanceDetailPane, instanceOverviewPane;
    @FXML private Label instanceNameLabel, instanceVersionBadge, instanceFabricBadge;
    @FXML private Label instanceModCountLabel, instanceIconLabel;
    @FXML private Button launchButton;
    @FXML private VBox progressBox;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;
    @FXML private VBox modListContainer;
    @FXML private Label noModsLabel;

    // Mods tab
    @FXML private TextField modSearchField;
    @FXML private ComboBox<String> modLoaderFilter, modVersionFilter;
    @FXML private VBox modResultsContainer;
    @FXML private Label modSearchStatus;
    @FXML private Button prevPageBtn, nextPageBtn;
    @FXML private Label pageLabel;

    // Skins tab
    @FXML private VBox skinEditorContainer;

    // Settings tab
    @FXML private TextField javaPathField, minMemField, maxMemField, jvmArgsField;
    @FXML private CheckBox closeOnLaunchCheck;

    // Log tab
    @FXML private TextArea logTextArea;

    // Services
    private final MicrosoftAuthService authService    = new MicrosoftAuthService();
    private final MinecraftVersionManager versionMgr  = new MinecraftVersionManager();
    private final FabricService fabricService          = new FabricService();
    private final ModrinthService modrinthService      = new ModrinthService();
    private final GameLauncher gameLauncher            = new GameLauncher();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r); t.setDaemon(true); return t;
    });

    private Instance selectedInstance;
    private List<MinecraftVersionManager.VersionInfo> allVersions;
    private int modSearchOffset = 0;
    private static final int PAGE_SIZE = 20;
    private int modSearchTotal = 0;

    public void initialize() {
        loadSettingsIntoUI();
        refreshInstanceList();
        setupModsTab();
        checkExistingAuth();
        loadVersionList();
        initSkinsTab();
    }

    // ── Skins tab ─────────────────────────────────────────────────────────────

    private void initSkinsTab() {
        SkinEditorController skinEditor = new SkinEditorController();
        VBox pane = skinEditor.buildPane(getStage());
        skinEditorContainer.getChildren().setAll(pane);
        VBox.setVgrow(pane, Priority.ALWAYS);
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private void checkExistingAuth() {
        MicrosoftAuthService.AuthResult stored = authService.getStoredAuth();
        if (stored != null) showLoggedIn(stored);
    }

    @FXML
    private void handleMicrosoftLogin() {
        OAuthLoginDialog dialog = new OAuthLoginDialog(authService, getStage());
        dialog.showAndWait().ifPresent(result -> {
            showLoggedIn(result);
            appendLog("Logged in as: " + result.username());
        });
    }

    @FXML
    private void handleLogout() {
        authService.logout();
        userInfoBox.setVisible(false);  userInfoBox.setManaged(false);
        loginButton.setVisible(true);   loginButton.setManaged(true);
        appendLog("Logged out.");
    }

    private void showLoggedIn(MicrosoftAuthService.AuthResult result) {
        usernameLabel.setText(result.username());
        loginButton.setVisible(false);  loginButton.setManaged(false);
        userInfoBox.setVisible(true);   userInfoBox.setManaged(true);
        executor.submit(() -> {
            try {
                String url = "https://crafatar.com/avatars/" + result.uuid() + "?size=36&overlay=true";
                Image img = new Image(url, 36, 36, true, true);
                Platform.runLater(() -> avatarImage.setImage(img));
            } catch (Exception ignored) {}
        });
    }

    // ── Instance list ─────────────────────────────────────────────────────────

    private void refreshInstanceList() {
        instanceListContainer.getChildren().clear();
        for (Instance inst : InstanceManager.getInstance().getInstances()) {
            InstanceCard card = new InstanceCard(inst, () -> selectInstance(inst));
            instanceListContainer.getChildren().add(card);
        }
    }

    private void selectInstance(Instance inst) {
        this.selectedInstance = inst;
        instanceListContainer.getChildren().forEach(n -> n.getStyleClass().remove("selected"));
        instanceListContainer.getChildren().stream()
                .filter(n -> n instanceof InstanceCard && ((InstanceCard) n).getInstance() == inst)
                .forEach(n -> n.getStyleClass().add("selected"));
        showInstanceDetail(inst);
        mainTabPane.getSelectionModel().select(instanceTab);
    }

    private void showInstanceDetail(Instance inst) {
        noInstancePane.setVisible(false);   noInstancePane.setManaged(false);
        instanceDetailPane.setVisible(true); instanceDetailPane.setManaged(true);

        instanceNameLabel.setText(inst.name);
        instanceVersionBadge.setText(inst.minecraftVersion);

        if (inst.useFabric && inst.fabricVersion != null) {
            instanceFabricBadge.setText("Fabric " + inst.fabricVersion);
            instanceFabricBadge.setVisible(true); instanceFabricBadge.setManaged(true);
        } else {
            instanceFabricBadge.setVisible(false); instanceFabricBadge.setManaged(false);
        }

        int mc = inst.mods != null ? inst.mods.size() : 0;
        instanceModCountLabel.setText(mc + " mod" + (mc == 1 ? "" : "s") + " installed");
        refreshModList(inst);
    }

    private void refreshModList(Instance inst) {
        modListContainer.getChildren().clear();
        if (inst.mods == null || inst.mods.isEmpty()) {
            modListContainer.getChildren().add(noModsLabel);
            return;
        }
        for (Instance.InstalledMod mod : inst.mods) {
            ModListItem item = new ModListItem(mod,
                    () -> handleToggleMod(inst, mod),
                    () -> handleRemoveMod(inst, mod));
            modListContainer.getChildren().add(item);
        }
    }

    // ── Instance actions ──────────────────────────────────────────────────────

    @FXML private void handleNewInstance() {
        new NewInstanceDialog(versionMgr, fabricService, getStage())
                .showAndWait().ifPresent(inst -> {
                    InstanceManager.getInstance().addInstance(inst);
                    refreshInstanceList();
                    selectInstance(inst);
                });
    }

    @FXML private void handleEditInstance() {
        if (selectedInstance == null) return;
        new EditInstanceDialog(selectedInstance, getStage())
                .showAndWait().ifPresent(updated -> {
                    InstanceManager.getInstance().updateInstance(updated);
                    refreshInstanceList();
                    showInstanceDetail(updated);
                });
    }

    @FXML private void handleDeleteInstance() {
        if (selectedInstance == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Instance");
        confirm.setHeaderText("Delete \"" + selectedInstance.name + "\"?");
        confirm.setContentText("This will permanently delete the instance and all its files.");
        confirm.initOwner(getStage());
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                InstanceManager.getInstance().deleteInstance(selectedInstance);
                selectedInstance = null;
                refreshInstanceList();
                noInstancePane.setVisible(true);   noInstancePane.setManaged(true);
                instanceDetailPane.setVisible(false); instanceDetailPane.setManaged(false);
            }
        });
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    @FXML private void handleLaunch() {
        if (selectedInstance == null) return;
        MicrosoftAuthService.AuthResult auth = authService.getStoredAuth();
        if (auth == null) { showAlert("Not Logged In", "Please sign in with Microsoft first."); return; }

        launchButton.setDisable(true);
        setProgress("Preparing...", 0);
        Instance inst = selectedInstance;

        executor.submit(() -> {
            try {
                if (!versionMgr.isVersionDownloaded(inst.minecraftVersion)) {
                    versionMgr.downloadVersion(inst.minecraftVersion,
                            (msg, p) -> Platform.runLater(() -> setProgress(msg, p)));
                }
                if (inst.useFabric && inst.fabricVersion != null
                        && !fabricService.isFabricInstalled(inst.minecraftVersion, inst.fabricVersion)) {
                    fabricService.installFabric(inst.minecraftVersion, inst.fabricVersion,
                            (msg, p) -> Platform.runLater(() -> setProgress(msg, p)));
                }
                Platform.runLater(() -> { setProgress("Launching...", 1.0); mainTabPane.getSelectionModel().selectLast(); });
                appendLog("=== Launching " + inst.name + " ===");
                Process process = gameLauncher.launch(inst, auth.username(), auth.uuid(), auth.accessToken(), this::appendLog);
                Platform.runLater(() -> { hideProgress(); launchButton.setDisable(false);
                    if (LauncherConfig.getInstance().getData().closeOnLaunch) getStage().close(); });
                int code = process.waitFor();
                appendLog("=== Game exited with code " + code + " ===");
                Platform.runLater(() -> launchButton.setDisable(false));
            } catch (Exception e) {
                logger.error("Launch failed", e);
                appendLog("ERROR: " + e.getMessage());
                Platform.runLater(() -> { hideProgress(); launchButton.setDisable(false); showAlert("Launch Failed", e.getMessage()); });
            }
        });
    }

    // ── Mods ──────────────────────────────────────────────────────────────────

    private void setupModsTab() {
        modLoaderFilter.getItems().addAll("fabric", "forge", "quilt", "neoforge");
        modLoaderFilter.setValue("fabric");
    }

    private void loadVersionList() {
        executor.submit(() -> {
            try {
                allVersions = versionMgr.fetchVersionList();
                Platform.runLater(() -> {
                    modVersionFilter.getItems().clear();
                    for (MinecraftVersionManager.VersionInfo v : allVersions) {
                        if (v.isRelease()) modVersionFilter.getItems().add(v.id);
                        if (modVersionFilter.getItems().size() >= 30) break;
                    }
                });
            } catch (Exception e) { logger.warn("Could not fetch versions: {}", e.getMessage()); }
        });
    }

    @FXML private void handleAddMod() {
        if (selectedInstance == null) { showAlert("No Instance", "Select an instance first."); return; }
        mainTabPane.getSelectionModel().select(modsTab);
        modVersionFilter.setValue(selectedInstance.minecraftVersion);
        if (selectedInstance.useFabric) modLoaderFilter.setValue("fabric");
    }

    @FXML private void handleModSearch() { modSearchOffset = 0; performModSearch(); }
    @FXML private void handlePrevPage()  { modSearchOffset = Math.max(0, modSearchOffset - PAGE_SIZE); performModSearch(); }
    @FXML private void handleNextPage()  { modSearchOffset += PAGE_SIZE; performModSearch(); }

    private void performModSearch() {
        String query = modSearchField.getText().trim();
        String version = modVersionFilter.getValue();
        String loader  = modLoaderFilter.getValue();
        modResultsContainer.getChildren().clear();
        modSearchStatus.setText("Searching...");
        prevPageBtn.setDisable(true); nextPageBtn.setDisable(true);

        executor.submit(() -> {
            try {
                ModrinthService.SearchResult result =
                        modrinthService.searchMods(query, version, loader, modSearchOffset, PAGE_SIZE);
                modSearchTotal = result.totalHits;
                Platform.runLater(() -> {
                    modResultsContainer.getChildren().clear();
                    modSearchStatus.setText(result.totalHits + " results");
                    result.hits.forEach(p -> modResultsContainer.getChildren().add(
                            new ModResultCard(p, () -> handleInstallMod(p))));
                    int cur = modSearchOffset / PAGE_SIZE + 1;
                    int tot = (int) Math.ceil((double) modSearchTotal / PAGE_SIZE);
                    pageLabel.setText("Page " + cur + " / " + tot);
                    prevPageBtn.setDisable(modSearchOffset == 0);
                    nextPageBtn.setDisable(modSearchOffset + PAGE_SIZE >= modSearchTotal);
                });
            } catch (Exception e) {
                Platform.runLater(() -> modSearchStatus.setText("Search failed: " + e.getMessage()));
            }
        });
    }

    private void handleInstallMod(ModrinthService.ModProject project) {
        if (selectedInstance == null) { showAlert("No Instance", "Select an instance first."); return; }
        String mcVersion = selectedInstance.minecraftVersion;
        String loader    = selectedInstance.useFabric ? "fabric" : "forge";
        executor.submit(() -> {
            try {
                List<ModrinthService.ModVersion> versions =
                        modrinthService.getModVersions(
                                project.project_id != null ? project.project_id : project.slug,
                                mcVersion, loader);
                if (versions.isEmpty()) {
                    Platform.runLater(() -> showAlert("Incompatible", "No version for MC " + mcVersion + " + " + loader));
                    return;
                }
                Instance.InstalledMod mod = modrinthService.installMod(
                        selectedInstance, project, versions.get(0), (msg, p) -> appendLog("[Mod] " + msg));
                selectedInstance.mods.add(mod);
                InstanceManager.getInstance().updateInstance(selectedInstance);
                Platform.runLater(() -> { refreshModList(selectedInstance); showInstanceDetail(selectedInstance);
                    appendLog("Installed: " + project.title + " " + versions.get(0).version_number); });
            } catch (Exception e) {
                logger.error("Mod install failed", e);
                Platform.runLater(() -> showAlert("Install Failed", e.getMessage()));
            }
        });
    }

    private void handleToggleMod(Instance inst, Instance.InstalledMod mod) {
        executor.submit(() -> {
            try { modrinthService.toggleMod(inst, mod); InstanceManager.getInstance().updateInstance(inst);
                Platform.runLater(() -> refreshModList(inst));
            } catch (Exception e) { Platform.runLater(() -> showAlert("Error", e.getMessage())); }
        });
    }

    private void handleRemoveMod(Instance inst, Instance.InstalledMod mod) {
        modrinthService.uninstallMod(inst, mod);
        inst.mods.remove(mod);
        InstanceManager.getInstance().updateInstance(inst);
        refreshModList(inst);
        showInstanceDetail(inst);
    }

    // ── Nav ───────────────────────────────────────────────────────────────────

    @FXML private void handleNavMods()     { mainTabPane.getSelectionModel().select(modsTab);     }
    @FXML private void handleNavSkins()    { mainTabPane.getSelectionModel().select(skinsTab);    }
    @FXML private void handleNavSettings() { mainTabPane.getSelectionModel().select(settingsTab); }

    // ── Settings ──────────────────────────────────────────────────────────────

    private void loadSettingsIntoUI() {
        LauncherConfig.ConfigData cfg = LauncherConfig.getInstance().getData();
        javaPathField.setText(cfg.javaPath);
        minMemField.setText(String.valueOf(cfg.minMemoryMb));
        maxMemField.setText(String.valueOf(cfg.maxMemoryMb));
        jvmArgsField.setText(cfg.jvmArgs);
        closeOnLaunchCheck.setSelected(cfg.closeOnLaunch);
    }

    @FXML private void handleSaveSettings() {
        LauncherConfig.ConfigData cfg = LauncherConfig.getInstance().getData();
        cfg.javaPath = javaPathField.getText().trim();
        try { cfg.minMemoryMb = Integer.parseInt(minMemField.getText().trim()); } catch (NumberFormatException ignored) {}
        try { cfg.maxMemoryMb = Integer.parseInt(maxMemField.getText().trim()); } catch (NumberFormatException ignored) {}
        cfg.jvmArgs = jvmArgsField.getText().trim();
        cfg.closeOnLaunch = closeOnLaunchCheck.isSelected();
        LauncherConfig.getInstance().save();
        showAlert("Saved", "Settings saved successfully.");
    }

    @FXML private void handleAutoDetectJava() {
        javaPathField.setText(com.minelauncher.utils.OsUtils.getJavaExecutable());
    }

    // ── Log ───────────────────────────────────────────────────────────────────

    @FXML private void handleClearLog() { logTextArea.clear(); }
    @FXML private void handleCopyLog() {
        ClipboardContent cc = new ClipboardContent();
        cc.putString(logTextArea.getText());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    private void appendLog(String line) {
        Platform.runLater(() -> { logTextArea.appendText(line + "\n"); logTextArea.setScrollTop(Double.MAX_VALUE); });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setProgress(String msg, double val) {
        progressBox.setVisible(true); progressBox.setManaged(true);
        progressLabel.setText(msg); progressBar.setProgress(val);
    }

    private void hideProgress() { progressBox.setVisible(false); progressBox.setManaged(false); }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        try { a.initOwner(getStage()); } catch (Exception ignored) {}
        a.showAndWait();
    }

    private Stage getStage() {
        try { return (Stage) mainTabPane.getScene().getWindow(); } catch (Exception e) { return null; }
    }
}
