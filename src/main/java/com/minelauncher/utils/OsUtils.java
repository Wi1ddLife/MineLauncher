package com.minelauncher.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class OsUtils {

    public enum OS { WINDOWS, MAC, LINUX }

    private static final OS CURRENT_OS;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            CURRENT_OS = OS.WINDOWS;
        } else if (os.contains("mac")) {
            CURRENT_OS = OS.MAC;
        } else {
            CURRENT_OS = OS.LINUX;
        }
    }

    public static OS getCurrentOs()   { return CURRENT_OS; }
    public static boolean isWindows() { return CURRENT_OS == OS.WINDOWS; }
    public static boolean isMac()     { return CURRENT_OS == OS.MAC; }
    public static boolean isLinux()   { return CURRENT_OS == OS.LINUX; }

    public static String getOsName() {
        return switch (CURRENT_OS) {
            case WINDOWS -> "windows";
            case MAC     -> "osx";
            case LINUX   -> "linux";
        };
    }

    public static String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("x86_64")  || arch.contains("amd64")) return "x64";
        return "x86";
    }

    public static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            String exe = isWindows() ? "java.exe" : "java";
            Path javaBin = Paths.get(javaHome, "bin", exe);
            if (javaBin.toFile().exists()) {
                return javaBin.toString();
            }
        }
        return isWindows() ? "java.exe" : "java";
    }
}
