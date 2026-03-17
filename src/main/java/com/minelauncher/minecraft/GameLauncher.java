package com.minelauncher.minecraft;

import com.google.gson.*;
import com.minelauncher.config.LauncherConfig;
import com.minelauncher.fabric.FabricService;
import com.minelauncher.instances.Instance;
import com.minelauncher.instances.InstanceManager;
import com.minelauncher.utils.FileUtils;
import com.minelauncher.utils.OsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class GameLauncher {

    private static final Logger logger = LoggerFactory.getLogger(GameLauncher.class);

    private final MinecraftVersionManager versionManager = new MinecraftVersionManager();
    private final FabricService fabricService = new FabricService();

    public Process launch(Instance instance, String username, String uuid, String accessToken,
                          Consumer<String> logConsumer) throws Exception {

        logger.info("Launching instance: {}", instance.name);
        String versionId = instance.minecraftVersion;
        JsonObject versionJson = versionManager.getVersionJson(versionId);

        // Build classpath
        List<Path> classpath = versionManager.getLibraryPaths(versionJson);

        // Add Fabric libraries if needed
        if (instance.useFabric && instance.fabricVersion != null) {
            classpath.addAll(fabricService.getFabricLibraryPaths(versionId, instance.fabricVersion));
        }

        // Add client jar
        Path clientJar = FileUtils.getVersionsDir().resolve(versionId).resolve(versionId + ".jar");
        classpath.add(clientJar);

        // Get asset index id
        String assetIndex = versionJson.getAsJsonObject("assetIndex").get("id").getAsString();

        // Determine main class
        String mainClass;
        if (instance.useFabric && instance.fabricVersion != null) {
            mainClass = fabricService.getMainClass(versionId, instance.fabricVersion);
        } else {
            mainClass = versionJson.get("mainClass").getAsString();
        }

        // Build JVM arguments
        LauncherConfig.ConfigData config = LauncherConfig.getInstance().getData();
        int maxMem = instance.maxMemoryMb > 0 ? instance.maxMemoryMb : config.maxMemoryMb;
        int minMem = instance.minMemoryMb > 0 ? instance.minMemoryMb : config.minMemoryMb;

        List<String> command = new ArrayList<>();
        command.add(OsUtils.getJavaExecutable());

        // Memory
        command.add("-Xms" + minMem + "m");
        command.add("-Xmx" + maxMem + "m");

        // Standard JVM args
        command.add("-XX:+UseG1GC");
        command.add("-XX:+ParallelRefProcEnabled");
        command.add("-XX:MaxGCPauseMillis=200");
        command.add("-XX:+UnlockExperimentalVMOptions");
        command.add("-XX:+DisableExplicitGC");
        command.add("-XX:+AlwaysPreTouch");
        command.add("-Djava.library.path=" + getNativesPath(versionId));
        command.add("-Dminecraft.launcher.brand=MineLauncher");
        command.add("-Dminecraft.launcher.version=1.0.0");

        // User custom JVM args
        String extraArgs = instance.jvmArgs != null ? instance.jvmArgs : config.jvmArgs;
        if (extraArgs != null && !extraArgs.isBlank()) {
            command.addAll(Arrays.asList(extraArgs.split("\\s+")));
        }

        // Classpath
        String cp = classpath.stream()
                .map(Path::toString)
                .reduce((a, b) -> a + File.pathSeparator + b)
                .orElse("");
        command.add("-cp");
        command.add(cp);

        // Main class
        command.add(mainClass);

        // Minecraft game arguments
        Path gameDir = InstanceManager.getInstance().getInstanceDir(instance);
        command.addAll(buildGameArgs(versionJson, username, uuid, accessToken, assetIndex, gameDir));

        logger.info("Launch command: {}", String.join(" ", command));

        writeModConfig(instance);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Pipe game output to log consumer
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    logConsumer.accept(finalLine);
                }
            } catch (IOException e) {
                logger.debug("Game output stream closed");
            }
        }, "GameOutput-" + instance.name);
        outputThread.setDaemon(true);
        outputThread.start();

        return process;
    }

    private List<String> buildGameArgs(JsonObject versionJson, String username, String uuid,
                                        String accessToken, String assetIndex, Path gameDir) {
        List<String> args = new ArrayList<>();

        // Try new argument format first
        if (versionJson.has("arguments")) {
            JsonObject arguments = versionJson.getAsJsonObject("arguments");
            if (arguments.has("game")) {
                for (JsonElement el : arguments.getAsJsonArray("game")) {
                    if (el.isJsonPrimitive()) {
                        String arg = el.getAsString();
                        args.add(resolveArgument(arg, username, uuid, accessToken, assetIndex, gameDir));
                    }
                }
            }
        } else if (versionJson.has("minecraftArguments")) {
            // Legacy format
            String minecraftArgs = versionJson.get("minecraftArguments").getAsString();
            for (String arg : minecraftArgs.split(" ")) {
                args.add(resolveArgument(arg, username, uuid, accessToken, assetIndex, gameDir));
            }
        }

        return args;
    }

    private String resolveArgument(String arg, String username, String uuid,
                                   String accessToken, String assetIndex, Path gameDir) {
        return arg
                .replace("${auth_player_name}", username)
                .replace("${version_name}", "MineLauncher")
                .replace("${game_directory}", gameDir.toString())
                .replace("${assets_root}", FileUtils.getAssetsDir().toString())
                .replace("${assets_index_name}", assetIndex)
                .replace("${auth_uuid}", uuid)
                .replace("${auth_access_token}", accessToken)
                .replace("${user_type}", "msa")
                .replace("${version_type}", "release")
                .replace("${clientid}", "")
                .replace("${auth_xuid}", "")
                .replace("${user_properties}", "{}");
    }

    private String getNativesPath(String versionId) throws IOException {
        Path nativesDir = FileUtils.getVersionsDir().resolve(versionId).resolve("natives");
        Files.createDirectories(nativesDir);
        return nativesDir.toString();
    }

    // Called before launch to write the mod config the in-game mod reads
    private void writeModConfig(Instance instance) {
        try {
            com.minelauncher.skin.SkinService skinService = new com.minelauncher.skin.SkinService();
            com.minelauncher.skin.CapeModel activeCape = skinService.getActiveCape();

            com.google.gson.JsonObject cfg = new com.google.gson.JsonObject();
            cfg.addProperty("showNametagIcon", true);
            cfg.addProperty("nametagIconStyle", "MINELAUNCHER");
            cfg.addProperty("overrideMojangCape", activeCape != null);

            if (activeCape != null) {
                cfg.addProperty("activeCapeId", activeCape.id);
                cfg.addProperty("activeCapeSource", activeCape.source);
                if (activeCape.localPath != null) {
                    cfg.addProperty("capePngPath", activeCape.localPath);
                }
            } else {
                cfg.addProperty("activeCapeId", "");
                cfg.addProperty("activeCapeSource", "NONE");
            }

            java.nio.file.Path gameDir = com.minelauncher.instances.InstanceManager
                    .getInstance().getInstanceDir(instance);
            java.nio.file.Path configFile = gameDir.resolve("minelauncher_mod.json");
            java.nio.file.Files.writeString(configFile,
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(cfg));
            logger.info("Wrote mod config to {}", configFile);
        } catch (Exception e) {
            logger.warn("Could not write mod config: {}", e.getMessage());
        }
    }
}