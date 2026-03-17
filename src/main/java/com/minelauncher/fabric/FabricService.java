package com.minelauncher.fabric;

import com.google.gson.*;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class FabricService {

    private static final Logger logger = LoggerFactory.getLogger(FabricService.class);
    private static final String FABRIC_META_URL = "https://meta.fabricmc.net/v2";

    public List<FabricLoaderVersion> getLoaderVersions(String minecraftVersion) throws Exception {
        String url = FABRIC_META_URL + "/versions/loader/" + minecraftVersion;
        String json = HttpClient.get(url);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

        List<FabricLoaderVersion> versions = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            JsonObject loader = obj.getAsJsonObject("loader");
            FabricLoaderVersion v = new FabricLoaderVersion();
            v.version = loader.get("version").getAsString();
            v.stable = loader.get("stable").getAsBoolean();
            versions.add(v);
        }
        return versions;
    }

    public List<String> getSupportedMinecraftVersions() throws Exception {
        String url = FABRIC_META_URL + "/versions/game";
        String json = HttpClient.get(url);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

        List<String> versions = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.get("stable").getAsBoolean()) {
                versions.add(obj.get("version").getAsString());
            }
        }
        return versions;
    }

    public void installFabric(String minecraftVersion, String loaderVersion,
                               BiConsumer<String, Double> progress) throws Exception {
        logger.info("Installing Fabric {} for MC {}", loaderVersion, minecraftVersion);

        String profileUrl = FABRIC_META_URL + "/versions/loader/" +
                minecraftVersion + "/" + loaderVersion + "/profile/json";

        progress.accept("Downloading Fabric profile...", 0.1);
        String profileJson = HttpClient.get(profileUrl);

        Path fabricDir = getFabricDir(minecraftVersion, loaderVersion);
        Files.createDirectories(fabricDir);
        Files.writeString(fabricDir.resolve("fabric-profile.json"), profileJson);

        // Download Fabric libraries
        JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
        JsonArray libraries = profile.getAsJsonArray("libraries");
        int total = libraries.size();
        int i = 0;

        for (JsonElement libEl : libraries) {
            i++;
            JsonObject lib = libEl.getAsJsonObject();
            String name = lib.get("name").getAsString();
            String libUrl = lib.has("url") ? lib.get("url").getAsString() :
                    "https://libraries.minecraft.net/";

            progress.accept("Fabric library: " + name, 0.1 + 0.8 * i / total);

            Path libPath = mavenNameToPath(name);
            Path dest = FileUtils.getLibrariesDir().resolve(libPath);
            if (!Files.exists(dest)) {
                String downloadUrl = libUrl + libPath.toString().replace("\\", "/");
                try {
                    FileUtils.downloadFile(downloadUrl, dest);
                } catch (Exception e) {
                    logger.warn("Could not download Fabric library {}: {}", name, e.getMessage());
                }
            }
        }

        progress.accept("Fabric installed!", 1.0);
        logger.info("Fabric installation complete");
    }

    public List<Path> getFabricLibraryPaths(String minecraftVersion, String loaderVersion) {
        Path fabricDir = getFabricDir(minecraftVersion, loaderVersion);
        Path profileFile = fabricDir.resolve("fabric-profile.json");
        if (!Files.exists(profileFile)) return new ArrayList<>();

        try {
            String json = Files.readString(profileFile);
            JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
            JsonArray libraries = profile.getAsJsonArray("libraries");
            List<Path> paths = new ArrayList<>();
            for (JsonElement libEl : libraries) {
                JsonObject lib = libEl.getAsJsonObject();
                Path libPath = mavenNameToPath(lib.get("name").getAsString());
                Path fullPath = FileUtils.getLibrariesDir().resolve(libPath);
                if (Files.exists(fullPath)) paths.add(fullPath);
            }
            return paths;
        } catch (Exception e) {
            logger.error("Error reading Fabric libraries", e);
            return new ArrayList<>();
        }
    }

    public String getMainClass(String minecraftVersion, String loaderVersion) {
        Path profileFile = getFabricDir(minecraftVersion, loaderVersion).resolve("fabric-profile.json");
        try {
            if (Files.exists(profileFile)) {
                String json = Files.readString(profileFile);
                JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
                if (profile.has("mainClass")) return profile.get("mainClass").getAsString();
            }
        } catch (Exception e) {
            logger.error("Error reading Fabric main class", e);
        }
        return "net.fabricmc.loader.impl.launch.knot.KnotClient";
    }

    public boolean isFabricInstalled(String minecraftVersion, String loaderVersion) {
        return Files.exists(getFabricDir(minecraftVersion, loaderVersion).resolve("fabric-profile.json"));
    }

    private Path getFabricDir(String minecraftVersion, String loaderVersion) {
        return FileUtils.getVersionsDir().resolve("fabric-" + minecraftVersion + "-" + loaderVersion);
    }

    private Path mavenNameToPath(String name) {
        // com.example:artifact:version -> com/example/artifact/version/artifact-version.jar
        String[] parts = name.split(":");
        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];
        return Paths.get(group, artifact, version, artifact + "-" + version + ".jar");
    }

    public static class FabricLoaderVersion {
        public String version;
        public boolean stable;

        @Override
        public String toString() { return version + (stable ? "" : " (unstable)"); }
    }
}
