package com.minelauncher.ui;

import com.minelauncher.instances.InstanceManager;
import com.minelauncher.ui.controllers.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainWindow {

    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    public void show(Stage stage) throws Exception {
        // Load instances
        InstanceManager.getInstance().loadAll();

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        Scene scene = new Scene(root, 1280, 780);
        scene.getStylesheets().add(getClass().getResource("/css/launcher.css").toExternalForm());

        stage.setTitle("MineLauncher");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(650);

        // Try to set icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/images/icon.png"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            logger.debug("No launcher icon found");
        }

        stage.show();
        controller.initialize();
        logger.info("Main window displayed");
    }
}
