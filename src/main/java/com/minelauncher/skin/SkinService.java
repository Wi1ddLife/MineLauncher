package com.minelauncher.skin;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public class SkinService {

    private static final Logger logger = LoggerFactory.getLogger(SkinService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SKIN_API = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final String CAPE_API = "https://api.minecraftservices.com/minecraft/profile/capes";

    private final Path skinsDir;
    private final Path capesDir;
    private final Path skinsJson;
    private final Path capesJson;

    private List<SkinModel> skins = new ArrayList<>();
    private List<CapeModel> capes = new ArrayList<>();

    public SkinService() {
        skinsDir  = FileUtils.getLauncherDir().resolve("skins");
        capesDir  = FileUtils.getLauncherDir().resolve("capes");
        skinsJson = FileUtils.getLauncherDir().resolve("skins.json");
        capesJson = FileUtils.getLauncherDir().resolve("capes.json");
        try {
            Files.createDirectories(skinsDir);
            Files.createDirectories(capesDir);
        } catch (IOException e) {
            logger.error("Could not create skin directories", e);
        }
        load();
        if (capes.isEmpty()) addBuiltInCapes();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void load() {
        skins = loadList(skinsJson, new TypeToken<List<SkinModel>>(){}.getType());
        capes = loadList(capesJson, new TypeToken<List<CapeModel>>(){}.getType());
    }

    private <T> List<T> loadList(Path path, Type type) {
        if (!Files.exists(path)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(path)) {
            List<T> list = GSON.fromJson(r, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(skinsJson)) { GSON.toJson(skins, w); }
        catch (IOException e) { logger.error("Could not save skins", e); }
        try (Writer w = Files.newBufferedWriter(capesJson)) { GSON.toJson(capes, w); }
        catch (IOException e) { logger.error("Could not save capes", e); }
    }

    // ── Skin operations ───────────────────────────────────────────────────────

    public List<SkinModel> getSkins() { return Collections.unmodifiableList(skins); }

    public SkinModel addSkin(String name, Path sourceFile, String variant) throws IOException {
        String filename = name.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".png";
        Path dest = skinsDir.resolve(filename);
        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        SkinModel skin = new SkinModel(name, dest.toString(), variant);
        skins.add(skin);
        save();
        return skin;
    }

    public void removeSkin(SkinModel skin) {
        skins.remove(skin);
        try { if (skin.localPath != null) Files.deleteIfExists(Paths.get(skin.localPath)); }
        catch (IOException ignored) {}
        save();
    }

    public void applySkin(SkinModel skin, String mcAccessToken) throws IOException {
        if (skin.localPath == null || !Files.exists(Paths.get(skin.localPath))) {
            throw new IOException("Skin file not found: " + skin.localPath);
        }

        // Read skin PNG as base64
        byte[] bytes = Files.readAllBytes(Paths.get(skin.localPath));
        String base64 = Base64.getEncoder().encodeToString(bytes);

        // POST to Minecraft skin API
        var jsonBody = new JsonObject();
        jsonBody.addProperty("variant", skin.variant.equals("SLIM") ? "slim" : "classic");
        jsonBody.addProperty("file", base64);

        // Use multipart form for skin upload
        uploadSkinMultipart(Paths.get(skin.localPath), skin.variant, mcAccessToken);

        skins.forEach(s -> s.isActive = false);
        skin.isActive = true;
        save();
        logger.info("Applied skin: {}", skin.name);
    }

    private void uploadSkinMultipart(Path skinFile, String variant, String token) throws IOException {
        okhttp3.MediaType PNG = okhttp3.MediaType.get("image/png");
        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(skinFile.toFile(), PNG);
        okhttp3.MultipartBody body = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("variant", variant.equals("SLIM") ? "slim" : "classic")
                .addFormDataPart("file", skinFile.getFileName().toString(), fileBody)
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(SKIN_API)
                .post(body)
                .header("Authorization", "Bearer " + token)
                .build();

        try (okhttp3.Response resp = HttpClient.getClient().newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Skin upload failed: HTTP " + resp.code()
                        + " " + (resp.body() != null ? resp.body().string() : ""));
            }
        }
    }

    // ── Cape operations ───────────────────────────────────────────────────────

    public List<CapeModel> getCapes() { return Collections.unmodifiableList(capes); }

    public void applyCape(CapeModel cape, String mcAccessToken) throws IOException {
        if ("MOJANG".equals(cape.source) && cape.id != null) {
            // Enable a Mojang cape by its ID
            var jsonBody = new JsonObject();
            jsonBody.addProperty("capeId", cape.id);
            HttpClient.post(CAPE_API, new Gson().toJson(jsonBody), mcAccessToken);
        }
        // For Lunar/Feather/Custom capes: cosmetic only (stored locally, noted in launch args)
        capes.forEach(c -> c.isActive = false);
        cape.isActive = true;
        save();
        logger.info("Set active cape: {} ({})", cape.name, cape.source);
    }

    public void removeCape(String mcAccessToken) throws IOException {
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(CAPE_API)
                .delete()
                .header("Authorization", "Bearer " + mcAccessToken)
                .build();
        try (okhttp3.Response resp = HttpClient.getClient().newCall(request).execute()) {
            if (!resp.isSuccessful() && resp.code() != 204) {
                throw new IOException("Remove cape failed: HTTP " + resp.code());
            }
        }
        capes.forEach(c -> c.isActive = false);
        save();
    }

    public CapeModel addCustomCape(String name, Path sourceFile) throws IOException {
        String filename = name.replaceAll("[^a-zA-Z0-9_-]", "_") + "_cape.png";
        Path dest = capesDir.resolve(filename);
        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        CapeModel cape = CapeModel.custom(name, dest.toString());
        capes.add(cape);
        save();
        return cape;
    }

    public CapeModel getActiveCape() {
        return capes.stream().filter(c -> c.isActive).findFirst().orElse(null);
    }

    public SkinModel getActiveSkin() {
        return skins.stream().filter(s -> s.isActive).findFirst().orElse(null);
    }

    private void addBuiltInCapes() {
        // Lunar Client capes (cosmetic display only)
        capes.add(CapeModel.lunar("Anniversary",   "https://img.icons8.com/color/96/moon-symbol.png"));
        capes.add(CapeModel.lunar("Migration",     "https://img.icons8.com/color/96/fly.png"));
        capes.add(CapeModel.lunar("Contributor",   "https://img.icons8.com/color/96/star.png"));
        capes.add(CapeModel.lunar("Halloween",     "https://img.icons8.com/color/96/ghost.png"));
        capes.add(CapeModel.lunar("Winter",        "https://img.icons8.com/color/96/snowflake.png"));

        // Feather Client capes
        capes.add(CapeModel.feather("Feather OG",   "https://img.icons8.com/color/96/feather.png"));
        capes.add(CapeModel.feather("Feather Gold", "https://img.icons8.com/color/96/trophy.png"));
        capes.add(CapeModel.feather("Feather Blue", "https://img.icons8.com/color/96/lightning-bolt.png"));

        save();
    }
}
