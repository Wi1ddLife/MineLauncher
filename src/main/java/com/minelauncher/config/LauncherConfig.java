package com.minelauncher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelauncher.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class LauncherConfig {

    private static final Logger logger = LoggerFactory.getLogger(LauncherConfig.class);
    private static final LauncherConfig INSTANCE = new LauncherConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigData data = new ConfigData();

    private LauncherConfig() {}

    public static LauncherConfig getInstance() {
        return INSTANCE;
    }

    public void load() {
        Path configFile = FileUtils.getLauncherDir().resolve("launcher_config.json");
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                data = GSON.fromJson(reader, ConfigData.class);
                if (data == null) data = new ConfigData();
                logger.info("Configuration loaded");
            } catch (Exception e) {
                logger.error("Error loading config, using defaults", e);
                data = new ConfigData();
            }
        }
    }

    public void save() {
        Path configFile = FileUtils.getLauncherDir().resolve("launcher_config.json");
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            logger.error("Error saving config", e);
        }
    }

    public ConfigData getData() {
        return data;
    }

    // ── Nested config classes ─────────────────────────────────────────────────

    public static class ConfigData {
        public String javaPath = "java";
        public int minMemoryMb = 512;
        public int maxMemoryMb = 2048;
        public String lastSelectedInstance = "";
        public boolean closeOnLaunch = false;
        public String jvmArgs = "";
        public AuthData auth = new AuthData();
    }

    public static class AuthData {
        public String accessToken = "";
        public String refreshToken = "";
        public String username = "";
        public String uuid = "";
        public long expiresAt = 0;
    }
}
