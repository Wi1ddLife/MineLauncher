#!/bin/bash
# MineLauncher setup - downloads the Gradle wrapper jar
# Run this once before using ./gradlew

set -e

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL_FALLBACK="https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"

echo "Setting up MineLauncher build environment..."

if [ -f "$WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar already exists, skipping download."
else
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p gradle/wrapper
    
    if command -v curl &>/dev/null; then
        curl -fL "$WRAPPER_URL" -o "$WRAPPER_JAR" || \
        curl -fL "$WRAPPER_URL_FALLBACK" -o "$WRAPPER_JAR"
    elif command -v wget &>/dev/null; then
        wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" || \
        wget -q "$WRAPPER_URL_FALLBACK" -O "$WRAPPER_JAR"
    else
        echo "ERROR: Neither curl nor wget found. Please install one and re-run."
        exit 1
    fi
    
    echo "gradle-wrapper.jar downloaded ($(du -h $WRAPPER_JAR | cut -f1))"
fi

chmod +x gradlew
echo ""
echo "Setup complete! Now run:"
echo "  ./gradlew run          (to launch the launcher)"
echo "  ./gradlew shadowJar    (to build a fat JAR)"
