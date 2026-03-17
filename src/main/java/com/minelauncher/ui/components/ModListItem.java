package com.minelauncher.ui.components;

import com.minelauncher.instances.Instance;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ModListItem extends HBox {

    public ModListItem(Instance.InstalledMod mod, Runnable onToggle, Runnable onRemove) {
        getStyleClass().add("mod-list-item");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(8, 12, 8, 12));
        setMaxWidth(Double.MAX_VALUE);

        // Enable/disable toggle
        ToggleButton toggle = new ToggleButton(mod.enabled ? "✓" : "✗");
        toggle.setSelected(mod.enabled);
        toggle.getStyleClass().add(mod.enabled ? "mod-toggle-on" : "mod-toggle-off");
        toggle.setOnAction(e -> {
            onToggle.run();
            toggle.setText(mod.enabled ? "✓" : "✗");
            toggle.getStyleClass().clear();
            toggle.getStyleClass().add(mod.enabled ? "mod-toggle-on" : "mod-toggle-off");
        });

        // Mod info
        VBox info = new VBox(2);
        Label nameLabel = new Label(mod.name);
        nameLabel.getStyleClass().add("mod-item-name");
        Label versionLabel = new Label("v" + mod.version + (mod.enabled ? "" : "  [disabled]"));
        versionLabel.getStyleClass().add("mod-item-version");
        info.getChildren().addAll(nameLabel, versionLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        // File size
        String sizeStr = "";
        if (mod.fileSizeBytes > 0) {
            sizeStr = formatSize(mod.fileSizeBytes);
        }
        Label sizeLabel = new Label(sizeStr);
        sizeLabel.getStyleClass().add("mod-item-size");

        // Remove button
        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("btn-icon-danger");
        removeBtn.setOnAction(e -> onRemove.run());
        removeBtn.setTooltip(new Tooltip("Remove mod"));

        getChildren().addAll(toggle, info, sizeLabel, removeBtn);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
