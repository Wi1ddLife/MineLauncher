@echo off
REM MineLauncher setup for Windows - downloads the Gradle wrapper jar
REM Run this once before using gradlew.bat

set WRAPPER_JAR=gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar

if exist "%WRAPPER_JAR%" (
    echo gradle-wrapper.jar already exists, skipping download.
    goto done
)

echo Downloading gradle-wrapper.jar...
mkdir gradle\wrapper 2>nul

where curl >nul 2>&1
if %ERRORLEVEL% == 0 (
    curl -fL "%WRAPPER_URL%" -o "%WRAPPER_JAR%"
    goto done
)

where powershell >nul 2>&1
if %ERRORLEVEL% == 0 (
    powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
    goto done
)

echo ERROR: Could not download wrapper. Install curl or ensure PowerShell is available.
exit /b 1

:done
echo.
echo Setup complete! Now run:
echo   gradlew.bat run
