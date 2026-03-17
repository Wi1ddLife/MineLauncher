package com.minelauncher.instances;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Instance {

    public String id;
    public String name;
    public String minecraftVersion;
    public String fabricVersion;        // null if vanilla
    public boolean useFabric;
    public String iconPath;
    public int minMemoryMb;
    public int maxMemoryMb;
    public String jvmArgs;
    public List<InstalledMod> mods;
    public long createdAt;
    public long lastPlayedAt;
    public long totalPlayTimeSeconds;

    public Instance() {
        this.id = UUID.randomUUID().toString();
        this.mods = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.minMemoryMb = 512;
        this.maxMemoryMb = 2048;
        this.jvmArgs = "";
        this.useFabric = false;
    }

    public Instance(String name, String minecraftVersion) {
        this();
        this.name = name;
        this.minecraftVersion = minecraftVersion;
    }

    public static class InstalledMod {
        public String projectId;
        public String versionId;
        public String name;
        public String fileName;
        public String version;
        public boolean enabled;
        public String downloadUrl;
        public String sha512;
        public long fileSizeBytes;

        public InstalledMod() {
            this.enabled = true;
        }
    }

    @Override
    public String toString() {
        return name + " [" + minecraftVersion + (useFabric ? " + Fabric " + fabricVersion : "") + "]";
    }
}
