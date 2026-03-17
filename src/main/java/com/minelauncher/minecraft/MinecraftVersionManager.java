package com.minelauncher.minecraft;

import com.google.gson.*;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.HttpClient;
import com.minelauncher.utils.OsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class MinecraftVersionManager {

    private static final Logger logger = LoggerFactory.getLogger(MinecraftVersionManager.class);
    private static final Gson GSON = new Gson();

    private static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";

    public List<VersionInfo> fetchVersionList() throws IOException {
        logger.info("Fetching version manifest...");
        String json = HttpClient.get(VERSION_MANIFEST_URL);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray versions = root.getAsJsonArray("versions");

        List<VersionInfo> result = new ArrayList<>();
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            VersionInfo info = new VersionInfo();
            info.id = v.get("id").getAsString();
            info.type = v.get("type").getAsString();
            info.url = v.get("url").getAsString();
            info.releaseTime = v.get("releaseTime").getAsString();
            result.add(info);
        }
        return result;
    }

    public JsonObject getVersionJson(String versionId) throws IOException {
        Path versionDir = FileUtils.getVersionsDir().resolve(versionId);
        Path versionFile = versionDir.resolve(versionId + ".json");

        if (!Files.exists(versionFile)) {
            // Fetch manifest to find URL
            String manifestJson = HttpClient.get(VERSION_MANIFEST_URL);
            JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
            String versionUrl = null;
            for (JsonElement el : manifest.getAsJsonArray("versions")) {
                JsonObject v = el.getAsJsonObject();
                if (v.get("id").getAsString().equals(versionId)) {
                    versionUrl = v.get("url").getAsString();
                    break;
                }
            }
            if (versionUrl == null) throw new IOException("Version not found: " + versionId);

            Files.createDirectories(versionDir);
            String versionJson = HttpClient.get(versionUrl);
            Files.writeString(versionFile, versionJson);
        }

        return JsonParser.parseString(Files.readString(versionFile)).getAsJsonObject();
    }

    public void downloadVersion(String versionId, BiConsumer<String, Double> progressCallback) throws IOException {
        logger.info("Downloading Minecraft {}", versionId);
        JsonObject versionJson = getVersionJson(versionId);

        // Download client JAR
        JsonObject downloads = versionJson.getAsJsonObject("downloads");
        if (downloads.has("client")) {
            JsonObject clientDownload = downloads.getAsJsonObject("client");
            String clientUrl = clientDownload.get("url").getAsString();
            Path clientJar = FileUtils.getVersionsDir().resolve(versionId).resolve(versionId + ".jar");
            if (!Files.exists(clientJar)) {
                progressCallback.accept("Downloading client JAR...", 0.1);
                FileUtils.downloadFile(clientUrl, clientJar);
            }
        }

        // Download libraries
        progressCallback.accept("Downloading libraries...", 0.2);
        downloadLibraries(versionJson, progressCallback);

        // Download assets
        progressCallback.accept("Downloading assets...", 0.6);
        downloadAssets(versionJson, progressCallback);

        progressCallback.accept("Done!", 1.0);
    }

    private void downloadLibraries(JsonObject versionJson, BiConsumer<String, Double> progress) throws IOException {
        JsonArray libraries = versionJson.getAsJsonArray("libraries");
        int total = libraries.size();
        int i = 0;
        for (JsonElement libEl : libraries) {
            JsonObject lib = libEl.getAsJsonObject();
            i++;
            if (!shouldDownloadLibrary(lib)) continue;

            String name = lib.get("name").getAsString();
            progress.accept("Library: " + name, 0.2 + (0.4 * i / total));

            if (!lib.has("downloads")) continue;
            JsonObject libDownloads = lib.getAsJsonObject("downloads");
            if (libDownloads.has("artifact")) {
                JsonObject artifact = libDownloads.getAsJsonObject("artifact");
                String path = artifact.get("path").getAsString();
                String url = artifact.get("url").getAsString();
                Path dest = FileUtils.getLibrariesDir().resolve(path);
                if (!Files.exists(dest) && url != null && !url.isEmpty()) {
                    try {
                        FileUtils.downloadFile(url, dest);
                    } catch (Exception e) {
                        logger.warn("Failed to download library {}: {}", name, e.getMessage());
                    }
                }
            }
        }
    }

    private boolean shouldDownloadLibrary(JsonObject lib) {
        if (!lib.has("rules")) return true;
        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allowed = false;
        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                String osName = os.has("name") ? os.get("name").getAsString() : "";
                if (osName.equals(OsUtils.getOsName())) {
                    allowed = action.equals("allow");
                }
            } else {
                allowed = action.equals("allow");
            }
        }
        return allowed;
    }

    private void downloadAssets(JsonObject versionJson, BiConsumer<String, Double> progress) throws IOException {
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
        String assetIndexId = assetIndex.get("id").getAsString();
        String assetIndexUrl = assetIndex.get("url").getAsString();

        Path indexDir = FileUtils.getAssetsDir().resolve("indexes");
        Path indexFile = indexDir.resolve(assetIndexId + ".json");
        Files.createDirectories(indexDir);

        if (!Files.exists(indexFile)) {
            String indexJson = HttpClient.get(assetIndexUrl);
            Files.writeString(indexFile, indexJson);
        }

        String indexJson = Files.readString(indexFile);
        JsonObject objects = JsonParser.parseString(indexJson)
                .getAsJsonObject().getAsJsonObject("objects");

        Path objectsDir = FileUtils.getAssetsDir().resolve("objects");
        int total = objects.size();
        int i = 0;
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            i++;
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path dest = objectsDir.resolve(prefix).resolve(hash);
            if (!Files.exists(dest)) {
                String url = RESOURCES_URL + prefix + "/" + hash;
                try {
                    FileUtils.downloadFile(url, dest);
                } catch (Exception e) {
                    logger.warn("Failed to download asset {}: {}", entry.getKey(), e.getMessage());
                }
            }
            if (i % 100 == 0) {
                progress.accept("Assets: " + i + "/" + total, 0.6 + (0.35 * i / total));
            }
        }
    }

    public List<Path> getLibraryPaths(JsonObject versionJson) {
        List<Path> paths = new ArrayList<>();
        JsonArray libraries = versionJson.getAsJsonArray("libraries");
        for (JsonElement libEl : libraries) {
            JsonObject lib = libEl.getAsJsonObject();
            if (!shouldDownloadLibrary(lib)) continue;
            if (!lib.has("downloads")) continue;
            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (downloads.has("artifact")) {
                String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                Path libPath = FileUtils.getLibrariesDir().resolve(path);
                if (Files.exists(libPath)) paths.add(libPath);
            }
        }
        return paths;
    }

    public boolean isVersionDownloaded(String versionId) {
        Path clientJar = FileUtils.getVersionsDir().resolve(versionId).resolve(versionId + ".jar");
        return Files.exists(clientJar);
    }

    public static class VersionInfo {
        public String id;
        public String type;
        public String url;
        public String releaseTime;

        public boolean isRelease() { return "release".equals(type); }
        public boolean isSnapshot() { return "snapshot".equals(type); }
    }
}
