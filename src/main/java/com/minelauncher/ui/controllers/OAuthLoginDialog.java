package com.minelauncher.ui.controllers;

import com.minelauncher.auth.AuthException;
import com.minelauncher.auth.MicrosoftAuthService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Login dialog using JavaFX WebView.
 *
 * Key insight: we load the Microsoft login page but INTERCEPT the navigation
 * BEFORE the WebView actually loads the redirect URL. This way:
 *   - The login page itself loads fine (it's just HTML/CSS/JS)
 *   - Microsoft redirects to login.live.com/oauth20_desktop.srf?code=...
 *   - We cancel that navigation, extract the code, do the auth chain ourselves
 *
 * The "can't send a code" issue was caused by Microsoft detecting the old
 * WebKit user agent. We override it to look like a modern browser.
 */
public class OAuthLoginDialog extends Dialog<MicrosoftAuthService.AuthResult> {

    private static final Logger logger = LoggerFactory.getLogger(OAuthLoginDialog.class);
    private static final String REDIRECT_HOST = "login.microsoftonline.com/common/oauth2/nativeclient";

    private final MicrosoftAuthService authService;
    private final AtomicBoolean codeHandled = new AtomicBoolean(false);
    private Label statusLabel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public OAuthLoginDialog(MicrosoftAuthService authService, Stage owner) {
        this.authService = authService;
        setTitle("Sign in with Microsoft");
        setHeaderText(null);
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        getDialogPane().setPrefSize(500, 660);
        try {
            getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/launcher.css").toExternalForm());
        } catch (Exception ignored) {}
        buildUI();
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        setResultConverter(b -> null);
    }

    private void buildUI() {
        VBox root = new VBox(0);

        statusLabel = new Label("Sign in with your Microsoft / Xbox account.");
        statusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:12px; -fx-padding:8 12 4 12;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setWrapText(true);

        ProgressBar bar = new ProgressBar(-1);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(3);
        bar.setStyle("-fx-accent:#388bfd;");

        WebView webView = new WebView();
        VBox.setVgrow(webView, Priority.ALWAYS);
        root.getChildren().addAll(statusLabel, bar, webView);
        getDialogPane().setContent(root);

        WebEngine engine = webView.getEngine();

        // Override user agent to look like a real modern browser
        // This is the key fix — Microsoft blocks old WebKit UA strings
        engine.setUserAgent(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
        );

        // Hide/show progress bar
        engine.getLoadWorker().stateProperty().addListener((obs, o, n) -> {
            boolean loading = n == Worker.State.RUNNING || n == Worker.State.SCHEDULED;
            bar.setVisible(loading);
            bar.setManaged(loading);
        });

        // INTERCEPT every navigation — cancel it if it's the redirect URL
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SCHEDULED) {
                String url = engine.getLocation();
                if (url != null && url.contains(REDIRECT_HOST)) {
                    // Cancel the navigation immediately
                    engine.getLoadWorker().cancel();
                    handleRedirect(url, webView);
                }
            }
        });

        // Also watch locationProperty as a second interception point
        engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
            if (newUrl != null && newUrl.contains(REDIRECT_HOST)) {
                handleRedirect(newUrl, webView);
            }
        });

        engine.load(MicrosoftAuthService.buildAuthUrl());
    }

    private void handleRedirect(String url, WebView webView) {
        // Only handle once even if both listeners fire
        if (!codeHandled.compareAndSet(false, true)) return;

        logger.info("Intercepted redirect: {}", url);

        String code  = param(url, "code");
        String error = param(url, "error");

        if (code != null && !code.isBlank()) {
            Platform.runLater(() -> {
                webView.setDisable(true);
                setStatus("Signing in to Minecraft...", false);
            });

            executor.submit(() -> {
                try {
                    MicrosoftAuthService.AuthResult result = authService.loginWithCode(code);
                    Platform.runLater(() -> {
                        setResult(result);
                        close();
                    });
                } catch (AuthException e) {
                    logger.error("Auth failed", e);
                    codeHandled.set(false); // allow retry
                    Platform.runLater(() -> {
                        webView.setDisable(false);
                        setStatus("Login failed: " + e.getMessage(), true);
                    });
                }
            });

        } else {
            codeHandled.set(false);
            String desc = param(url, "error_description");
            setStatus("Login error: " + (desc != null ? desc : error), true);
        }
    }

    private void setStatus(String msg, boolean error) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle(
                (error ? "-fx-text-fill:#f44747;" : "-fx-text-fill:#8b949e;")
                + "-fx-font-size:12px; -fx-padding:8 12 4 12;");
        });
    }

    private String param(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) return null;
        for (String part : url.substring(q + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            if (part.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }
}
