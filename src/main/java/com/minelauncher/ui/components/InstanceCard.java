package com.minelauncher.ui.components;

import com.minelauncher.instances.Instance;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class InstanceCard extends HBox {

    private final Instance instance;

    public InstanceCard(Instance instance, Runnable onSelect) {
        this.instance = instance;

        getStyleClass().add("instance-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(8, 12, 8, 12));
        setMaxWidth(Double.MAX_VALUE);

        // Icon
        Label icon = new Label(instance.useFabric ? "🧵" : "🌍");
        icon.getStyleClass().add("instance-card-icon");

        // Text info
        VBox info = new VBox(2);
        Label nameLabel = new Label(instance.name);
        nameLabel.getStyleClass().add("instance-card-name");

        String versionText = instance.minecraftVersion;
        if (instance.useFabric && instance.fabricVersion != null) {
            versionText += " + Fabric";
        }
        Label versionLabel = new Label(versionText);
        versionLabel.getStyleClass().add("instance-card-version");

        info.getChildren().addAll(nameLabel, versionLabel);
        HBox.setHgrow(info, Priority.ALWAYS);

        getChildren().addAll(icon, info);
        setOnMouseClicked(e -> onSelect.run());
    }

    public Instance getInstance() {
        return instance;
    }
}
