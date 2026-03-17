# MineLauncher

A modern, fully-featured Minecraft Java Edition launcher built with JavaFX 21.

## ⚡ Quick Start (3 steps)

### Step 1 — Install Prerequisites

You need **JDK 21** (not JRE) and **internet access** for first run.

- **Windows:** Download from https://adoptium.net (choose JDK 21, MSI installer)
- **macOS:** `brew install --cask temurin@21`
- **Linux:** `sudo apt install openjdk-21-jdk` or `sudo dnf install java-21-openjdk-devel`

Verify: `java --version` should show `openjdk 21.x.x`

### Step 2 — Get the Gradle Wrapper JAR

The `gradle-wrapper.jar` binary is not included in source distributions (it's a binary).
Run the provided setup script to download it:

**Linux / macOS:**
```bash
chmod +x setup.sh gradlew
./setup.sh
```

**Windows:**
```cmd
setup.bat
```

This downloads `gradle/wrapper/gradle-wrapper.jar` (~60KB) from the official Gradle GitHub.

### Step 3 — Run

```bash
# Linux/macOS
./gradlew run

# Windows
gradlew.bat run
```

Gradle will download itself (~140MB) on first run, then compile and launch the app.

---

## Alternative: Use System Gradle

If you already have Gradle 7+ installed system-wide, skip the wrapper entirely:

```bash
gradle run
```

Or build a fat JAR:
```bash
gradle shadowJar
java -jar build/libs/minelauncher-1.0.0-all.jar
```

---

## Alternative: Use IntelliJ IDEA (Easiest)

1. Open IntelliJ IDEA → **File → Open** → select the `launcher/` folder
2. IntelliJ detects the Gradle project and offers to **Download Gradle** — click yes
3. Wait for indexing, then right-click `LauncherApp.java` → **Run**

IntelliJ handles the wrapper jar automatically.

---

## Features

- **Microsoft OAuth Authentication** — Full MS → Xbox Live → XSTS → Minecraft auth chain
- **Fabric Mod Loader** — Install and manage Fabric loader versions per instance
- **Modrinth Integration** — Search and install mods directly from Modrinth
- **Multi-Instance Manager** — Create, edit, delete, and launch multiple game instances
- **Auto-Download** — Automatically downloads game files, libraries, and assets on launch
- **Dark Modern UI** — GitHub-dark inspired theme with smooth layout

---

## Project Structure

```
launcher/
├── setup.sh / setup.bat              ← Run first to get gradle-wrapper.jar
├── build.gradle                      ← Gradle build (JavaFX + Shadow JAR)
├── settings.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.properties
│   └── gradle-wrapper.jar            ← Downloaded by setup.sh (not in repo)
└── src/main/
    ├── java/com/minelauncher/
    │   ├── LauncherApp.java              Entry point
    │   ├── auth/
    │   │   └── MicrosoftAuthService.java  MS→XBL→XSTS→MC auth chain
    │   ├── config/LauncherConfig.java    Persistent settings (JSON)
    │   ├── fabric/FabricService.java     Fabric meta API + installer
    │   ├── instances/
    │   │   ├── Instance.java             Instance model
    │   │   └── InstanceManager.java      Load/save/delete instances
    │   ├── minecraft/
    │   │   ├── MinecraftVersionManager.java  Version manifest + downloader
    │   │   └── GameLauncher.java             JVM process builder
    │   ├── modrinth/ModrinthService.java  Modrinth API search + install
    │   ├── ui/
    │   │   ├── controllers/MainController.java   Central FXML controller
    │   │   ├── controllers/OAuthLoginDialog.java  WebView OAuth popup
    │   │   ├── controllers/NewInstanceDialog.java
    │   │   ├── controllers/EditInstanceDialog.java
    │   │   └── components/               InstanceCard, ModListItem, ModResultCard
    │   └── utils/                        FileUtils, HttpClient, OsUtils
    └── resources/
        ├── fxml/main.fxml               Full UI layout
        ├── css/launcher.css             Dark theme stylesheet
        └── logback.xml
```

---

## Authentication Flow

1. Click **Login with Microsoft** in the top-right
2. A browser popup (WebView) opens the Microsoft login page
3. Sign in with your Microsoft account linked to Minecraft
4. The launcher detects the OAuth redirect and extracts the auth code
5. Performs the full token chain: MS Token → XBL → XSTS → Minecraft
6. Your username and avatar appear in the top bar

---

## Launching Minecraft

1. Click **+ New Instance**
2. Pick a Minecraft version, optionally enable Fabric
3. Select the instance in the sidebar
4. Click **▶ LAUNCH** — missing files download automatically
5. Game output appears in the **Log** tab

---

## Data Location

| OS | Path |
|---|---|
| Windows | `%APPDATA%\.minelauncher\` |
| macOS | `~/Library/Application Support/.minelauncher/` |
| Linux | `~/.minelauncher/` |

Contains: `instances/`, `versions/`, `assets/`, `libraries/`, `launcher_config.json`
