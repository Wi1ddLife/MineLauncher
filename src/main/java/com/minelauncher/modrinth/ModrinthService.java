package com.minelauncher.modrinth;

import com.google.gson.*;
import com.minelauncher.instances.Instance;
import com.minelauncher.instances.InstanceManager;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class ModrinthService {

    private static final Logger logger = LoggerFactory.getLogger(ModrinthService.class);
    private static final Gson GSON = new Gson();
    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "MineLauncher/1.0.0 (contact@minelauncher.com)";

    public SearchResult searchMods(String query, String minecraftVersion,
                                   String loaderType, int offset, int limit) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL + "/search");
        url.append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);
        url.append("&project_type=mod");

        // Build facets
        List<String> facets = new ArrayList<>();
        if (minecraftVersion != null && !minecraftVersion.isEmpty()) {
            facets.add("[\"versions:" + minecraftVersion + "\"]");
        }
        if (loaderType != null && !loaderType.isEmpty()) {
            facets.add("[\"categories:" + loaderType + "\"]");
        }
        if (!facets.isEmpty()) {
            url.append("&facets=[").append(String.join(",", facets)).append("]");
        }

        String response = getWithUserAgent(url.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        SearchResult result = new SearchResult();
        result.totalHits = json.get("total_hits").getAsInt();
        result.offset = json.get("offset").getAsInt();
        result.limit = json.get("limit").getAsInt();
        result.hits = new ArrayList<>();

        for (JsonElement el : json.getAsJsonArray("hits")) {
            result.hits.add(GSON.fromJson(el, ModProject.class));
        }
        return result;
    }

    public List<ModVersion> getModVersions(String projectId, String minecraftVersion,
                                            String loader) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL + "/project/" + projectId + "/version");
        List<String> params = new ArrayList<>();
        if (minecraftVersion != null) {
            params.add("game_versions=[\"" + minecraftVersion + "\"]");
        }
        if (loader != null) {
            params.add("loaders=[\"" + loader + "\"]");
        }
        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }

        String response = getWithUserAgent(url.toString());
        JsonArray arr = JsonParser.parseString(response).getAsJsonArray();
        List<ModVersion> versions = new ArrayList<>();
        for (JsonElement el : arr) {
            versions.add(GSON.fromJson(el, ModVersion.class));
        }
        return versions;
    }

    public ModProject getProject(String projectId) throws IOException {
        String response = getWithUserAgent(BASE_URL + "/project/" + projectId);
        return GSON.fromJson(response, ModProject.class);
    }

    public Instance.InstalledMod installMod(Instance instance, ModProject project,
                                             ModVersion version,
                                             BiConsumer<String, Double> progress) throws IOException {
        // Find the primary file
        ModVersion.File primaryFile = null;
        for (ModVersion.File f : version.files) {
            if (f.primary) { primaryFile = f; break; }
        }
        if (primaryFile == null && version.files.length > 0) primaryFile = version.files[0];
        if (primaryFile == null) throw new IOException("No downloadable file found for " + project.title);

        Path modsDir = InstanceManager.getInstance().getModsDir(instance);
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(primaryFile.filename);

        progress.accept("Downloading " + project.title + "...", 0.0);
        FileUtils.downloadFile(primaryFile.url, dest);
        progress.accept("Installed " + project.title, 1.0);

        Instance.InstalledMod mod = new Instance.InstalledMod();
        mod.projectId = project.project_id != null ? project.project_id : project.slug;
        mod.versionId = version.id;
        mod.name = project.title;
        mod.fileName = primaryFile.filename;
        mod.version = version.version_number;
        mod.enabled = true;
        mod.downloadUrl = primaryFile.url;
        mod.fileSizeBytes = primaryFile.size;
        if (primaryFile.hashes != null) mod.sha512 = primaryFile.hashes.sha512;

        return mod;
    }

    public void uninstallMod(Instance instance, Instance.InstalledMod mod) {
        Path modFile = InstanceManager.getInstance().getModsDir(instance).resolve(mod.fileName);
        try {
            Files.deleteIfExists(modFile);
            // Also delete disabled version
            Path disabledFile = InstanceManager.getInstance().getModsDir(instance)
                    .resolve(mod.fileName + ".disabled");
            Files.deleteIfExists(disabledFile);
        } catch (IOException e) {
            logger.error("Error deleting mod file", e);
        }
    }

    public void toggleMod(Instance instance, Instance.InstalledMod mod) throws IOException {
        Path modsDir = InstanceManager.getInstance().getModsDir(instance);
        if (mod.enabled) {
            // Disable: rename to .disabled
            Path src = modsDir.resolve(mod.fileName);
            Path dst = modsDir.resolve(mod.fileName + ".disabled");
            if (Files.exists(src)) Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            mod.enabled = false;
        } else {
            // Enable: rename back
            Path src = modsDir.resolve(mod.fileName + ".disabled");
            Path dst = modsDir.resolve(mod.fileName);
            if (Files.exists(src)) Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            mod.enabled = true;
        }
    }

    private String getWithUserAgent(String url) throws IOException {
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        try (okhttp3.Response response = HttpClient.getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return response.body().string();
        }
    }

    // Data classes matching Modrinth API

    public static class SearchResult {
        public List<ModProject> hits;
        public int offset;
        public int limit;
        public int totalHits;
    }

    public static class ModProject {
        public String project_id;
        public String slug;
        public String title;
        public String description;
        public String[] categories;
        public String[] versions;
        public int downloads;
        public int follows;
        public String icon_url;
        public String project_type;
        public String date_created;
        public String date_modified;
        public String latest_version;
        public String license;
        public String[] gallery;
    }

    public static class ModVersion {
        public String id;
        public String project_id;
        public String name;
        public String version_number;
        public String changelog;
        public Dependency[] dependencies;
        public String[] game_versions;
        public String version_type;
        public String[] loaders;
        public boolean featured;
        public String status;
        public File[] files;

        public static class File {
            public Hashes hashes;
            public String url;
            public String filename;
            public boolean primary;
            public long size;
        }

        public static class Hashes {
            public String sha512;
            public String sha1;
        }

        public static class Dependency {
            public String version_id;
            public String project_id;
            public String dependency_type;
        }
    }
}
