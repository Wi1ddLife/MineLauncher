package com.minelauncher.skin;

import java.util.UUID;

public class SkinModel {
    public String id;
    public String name;
    public String variant;      // "CLASSIC" or "SLIM"
    public String localPath;    // path to local .png file
    public String url;          // remote URL if from Mojang API
    public boolean isActive;
    public long addedAt;

    public SkinModel() {
        this.id = UUID.randomUUID().toString();
        this.addedAt = System.currentTimeMillis();
        this.variant = "CLASSIC";
    }

    public SkinModel(String name, String localPath, String variant) {
        this();
        this.name = name;
        this.localPath = localPath;
        this.variant = variant;
    }
}
