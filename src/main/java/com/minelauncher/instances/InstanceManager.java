package com.minelauncher.instances;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minelauncher.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class InstanceManager {

    private static final Logger logger = LoggerFactory.getLogger(InstanceManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final InstanceManager INSTANCE = new InstanceManager();

    private final List<Instance> instances = new ArrayList<>();

    private InstanceManager() {}

    public static InstanceManager getInstance() { return INSTANCE; }

    public void loadAll() {
        instances.clear();
        Path instancesDir = FileUtils.getInstancesDir();
        try {
            if (!Files.exists(instancesDir)) return;
            Files.list(instancesDir).filter(Files::isDirectory).forEach(dir -> {
                Path config = dir.resolve("instance.json");
                if (Files.exists(config)) {
                    try (Reader r = Files.newBufferedReader(config)) {
                        Instance inst = GSON.fromJson(r, Instance.class);
                        if (inst != null) instances.add(inst);
                    } catch (Exception e) {
                        logger.error("Failed to load instance from {}", dir, e);
                    }
                }
            });
            logger.info("Loaded {} instances", instances.size());
        } catch (Exception e) {
            logger.error("Error loading instances", e);
        }
    }

    public void saveInstance(Instance instance) {
        Path dir = getInstanceDir(instance);
        try {
            Files.createDirectories(dir);
            Files.createDirectories(dir.resolve("mods"));
            Files.createDirectories(dir.resolve("resourcepacks"));
            Files.createDirectories(dir.resolve("screenshots"));
            try (Writer w = Files.newBufferedWriter(dir.resolve("instance.json"))) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            logger.error("Failed to save instance {}", instance.name, e);
        }
    }

    public void addInstance(Instance instance) {
        instances.add(instance);
        saveInstance(instance);
    }

    public void deleteInstance(Instance instance) {
        instances.remove(instance);
        Path dir = getInstanceDir(instance);
        try {
            deleteDirectory(dir);
        } catch (Exception e) {
            logger.error("Error deleting instance directory", e);
        }
    }

    public void updateInstance(Instance instance) {
        saveInstance(instance);
    }

    public List<Instance> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public Optional<Instance> findById(String id) {
        return instances.stream().filter(i -> i.id.equals(id)).findFirst();
    }

    public Path getInstanceDir(Instance instance) {
        return FileUtils.getInstancesDir().resolve(instance.id);
    }

    public Path getModsDir(Instance instance) {
        return getInstanceDir(instance).resolve("mods");
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) { /* ignore */ }
                });
    }
}
