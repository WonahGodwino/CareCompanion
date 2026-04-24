# CareCompanion

CareCompanion is an Android application focused on care operations workflows, with support for fingerprint device integration, offline-capable local data storage, and modern Jetpack-based UI architecture.

## Tech Stack

- Kotlin (Android)
- Jetpack Compose (UI)
- Hilt (Dependency Injection)
- Room (Local Database)
- Retrofit + OkHttp (Networking)
- WorkManager (Background tasks)
- SecuGen FDx SDK (Fingerprint integration)

## Project Configuration

- Application ID: `com.carecompanion`
- Min SDK: `24`
- Target SDK: `34`
- Compile SDK: `34`
- Build system: Gradle (Kotlin DSL)

## Main App Entry Points

- Application class: `CareCompanionApplication`
- Launcher activity: `SplashScreenActivity`
- Core activities:
  - `MainActivity`
  - `SetupActivity`

## Getting Started

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK 34
- Java 17 (for modern Android Gradle builds)
- A physical Android phone with USB debugging enabled (recommended for hardware integration)

### 1) Clone

```bash
git clone https://github.com/WonahGodwino/CareCompanion.git
cd CareCompanion
```

### 2) Configure local properties

Ensure `local.properties` points to your Android SDK path, for example:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### 3) Build debug APK

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

### 4) Install on a connected phone

```powershell
.\gradlew.bat installDebug
```

If multiple devices are connected, specify a device using `adb -s <serial> install` workflows.

## Security Notes

Security-sensitive and local-only files are excluded via `.gitignore`, including:

- Signing keys and keystore files
- Local SDK and machine configuration
- Local database dumps and snapshots
- Environment/secrets files
- Generated build artifacts and logs

Do not commit credentials, API keys, or private certificates.

## Suggested Development Commands

```powershell
# Clean build artifacts
.\gradlew.bat clean

# Build debug APK
.\gradlew.bat assembleDebug

# Install debug APK to connected device
.\gradlew.bat installDebug

# Run unit tests
.\gradlew.bat testDebugUnitTest
```

## Repository

- GitHub: https://github.com/WonahGodwino/CareCompanion

## License

Add your preferred license (MIT, Apache-2.0, or proprietary) before public release.