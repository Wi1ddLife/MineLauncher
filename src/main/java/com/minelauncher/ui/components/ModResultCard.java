package com.minelauncher.ui.components;

import com.minelauncher.modrinth.ModrinthService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModResultCard extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(ModResultCard.class);

    public ModResultCard(ModrinthService.ModProject project, Runnable onInstall) {
        getStyleClass().add("mod-result-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(14);
        setPadding(new Insets(14, 16, 14, 16));
        setMaxWidth(Double.MAX_VALUE);

        // Icon
        ImageView iconView = new ImageView();
        iconView.setFitWidth(48);
        iconView.setFitHeight(48);
        iconView.getStyleClass().add("mod-result-icon");
        iconView.setPreserveRatio(true);

        // Try loading icon async
        if (project.icon_url != null && !project.icon_url.isEmpty()) {
            new Thread(() -> {
                try {
                    Image img = new Image(project.icon_url, 48, 48, true, true, true);
                    Platform.runLater(() -> iconView.setImage(img));
                } catch (Exception e) {
                    logger.debug("Could not load mod icon: {}", project.title);
                }
            }, "icon-loader").start();
        }

        // Mod info
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label nameLabel = new Label(project.title);
        nameLabel.getStyleClass().add("mod-result-name");

        Label descLabel = new Label(project.description);
        descLabel.getStyleClass().add("mod-result-desc");
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(500);

        HBox tags = new HBox(6);
        tags.setAlignment(Pos.CENTER_LEFT);
        if (project.categories != null) {
            for (int i = 0; i < Math.min(3, project.categories.length); i++) {
                Label tag = new Label(project.categories[i]);
                tag.getStyleClass().add("mod-tag");
                tags.getChildren().add(tag);
            }
        }

        // Stats
        HBox stats = new HBox(14);
        stats.setAlignment(Pos.CENTER_LEFT);
        Label dlLabel = new Label("⬇ " + formatNumber(project.downloads));
        dlLabel.getStyleClass().add("mod-stat");
        Label followLabel = new Label("♥ " + formatNumber(project.follows));
        followLabel.getStyleClass().add("mod-stat");
        stats.getChildren().addAll(dlLabel, followLabel);

        info.getChildren().addAll(nameLabel, descLabel, tags, stats);

        // Install button
        Button installBtn = new Button("Install");
        installBtn.getStyleClass().add("btn-primary");
        installBtn.setOnAction(e -> {
            installBtn.setText("Installing...");
            installBtn.setDisable(true);
            onInstall.run();
            Platform.runLater(() -> {
                installBtn.setText("Installed ✓");
                installBtn.getStyleClass().add("btn-success");
            });
        });

        getChildren().addAll(iconView, info, installBtn);
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
