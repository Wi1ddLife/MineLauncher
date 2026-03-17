package com.minelauncher.skin;

public class CapeModel {
    public String id;
    public String name;
    public String source;       // "MOJANG", "LUNAR", "FEATHER", "CUSTOM"
    public String previewUrl;
    public String localPath;
    public boolean isActive;

    public static CapeModel lunar(String name, String previewUrl) {
        CapeModel c = new CapeModel();
        c.id = "lunar_" + name.toLowerCase().replace(" ", "_");
        c.name = name; c.source = "LUNAR"; c.previewUrl = previewUrl;
        return c;
    }

    public static CapeModel feather(String name, String previewUrl) {
        CapeModel c = new CapeModel();
        c.id = "feather_" + name.toLowerCase().replace(" ", "_");
        c.name = name; c.source = "FEATHER"; c.previewUrl = previewUrl;
        return c;
    }

    public static CapeModel custom(String name, String localPath) {
        CapeModel c = new CapeModel();
        c.id = "custom_" + System.currentTimeMillis();
        c.name = name; c.source = "CUSTOM"; c.localPath = localPath;
        return c;
    }
}
