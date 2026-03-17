package com.minelauncher.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.security.MessageDigest;

public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    private static Path launcherDir;

    public static void initializeLauncherDirectories() throws IOException {
        launcherDir = getLauncherDir();
        Files.createDirectories(launcherDir);
        Files.createDirectories(launcherDir.resolve("instances"));
        Files.createDirectories(launcherDir.resolve("versions"));
        Files.createDirectories(launcherDir.resolve("assets"));
        Files.createDirectories(launcherDir.resolve("libraries"));
        Files.createDirectories(launcherDir.resolve("java"));
        logger.info("Launcher directories initialized at: {}", launcherDir);
    }

    public static Path getLauncherDir() {
        if (launcherDir != null) return launcherDir;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            launcherDir = Paths.get(System.getenv("APPDATA"), ".minelauncher");
        } else if (os.contains("mac")) {
            launcherDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support", ".minelauncher");
        } else {
            launcherDir = Paths.get(System.getProperty("user.home"), ".minelauncher");
        }
        return launcherDir;
    }

    public static Path getInstancesDir() {
        return getLauncherDir().resolve("instances");
    }

    public static Path getVersionsDir() {
        return getLauncherDir().resolve("versions");
    }

    public static Path getAssetsDir() {
        return getLauncherDir().resolve("assets");
    }

    public static Path getLibrariesDir() {
        return getLauncherDir().resolve("libraries");
    }

    public static void downloadFile(String url, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try (InputStream in = new URL(url).openStream();
             ReadableByteChannel channel = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(destination.toFile())) {
            fos.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
        }
    }

    public static String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static boolean verifySha1(Path file, String expectedHash) {
        try {
            return expectedHash.equalsIgnoreCase(sha1(file));
        } catch (Exception e) {
            return false;
        }
    }

    public static String readString(Path path) throws IOException {
        return Files.readString(path);
    }

    public static void writeString(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
