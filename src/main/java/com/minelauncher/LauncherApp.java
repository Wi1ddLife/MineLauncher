package com.minelauncher;

import com.minelauncher.config.LauncherConfig;
import com.minelauncher.ui.MainWindow;
import com.minelauncher.utils.FileUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(LauncherApp.class);

    public static void main(String[] args) {
        logger.info("Starting MineLauncher v1.0.0");
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialize launcher directories
            FileUtils.initializeLauncherDirectories();

            // Load configuration
            LauncherConfig.getInstance().load();

            // Launch main window
            MainWindow mainWindow = new MainWindow();
            mainWindow.show(primaryStage);

        } catch (Exception e) {
            logger.error("Fatal error starting launcher", e);
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        logger.info("Launcher shutting down");
        try {
            LauncherConfig.getInstance().save();
        } catch (Exception e) {
            logger.error("Error saving config on shutdown", e);
        }
    }
}
