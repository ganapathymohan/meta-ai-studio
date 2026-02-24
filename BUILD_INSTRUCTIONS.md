# ╔══════════════════════════════════════════════════════════════╗
# ║         META AI STUDIO WRAPPER — BUILD INSTRUCTIONS         ║
# ╚══════════════════════════════════════════════════════════════╝

## Project Overview
- **App Name:** Meta AI Studio
- **Package ID:** com.metaai.studio
- **Min SDK:** 23 (Android 6.0 Marshmallow)
- **Target SDK:** 34 (Android 14)
- **Language:** Kotlin
- **Architecture:** Single Activity (AndroidX)

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Android Studio | Hedgehog 2023.1.1+ | https://developer.android.com/studio |
| JDK | 11 or 17 | Bundled with Android Studio |
| Android SDK | API 34 | Install via Android Studio SDK Manager |
| Gradle | 8.4 (auto-downloaded) | Handled by wrapper |

---

## Step 1 — Open the Project in Android Studio

1. Launch **Android Studio**
2. Click **"Open"**
3. Navigate to this folder:  
   `C:\Users\Ganapathy\docker-apps\maybe\meta apk\`
4. Click **OK**
5. Wait for **Gradle sync** to complete (it downloads all dependencies automatically)

> ⚠️ If prompted about the Gradle wrapper JAR being missing, click  
> **"Use Gradle Wrapper (recommended)"** — Android Studio will generate it.

---

## Step 2 — Generate Launcher Icons (Required)

The project uses adaptive icon XML for API 26+ devices. For older devices (API 23–25), you must generate bitmap PNGs:

1. In Android Studio, right-click `app/src/main/res`
2. Select **New → Image Asset**
3. Under **Icon Type**, select **"Launcher Icons (Adaptive and Legacy)"**
4. Under **Source Asset**, choose **"Image"** and upload your logo PNG, OR use **"Text"** and type `M`
5. Set **Background Color** to `#1A0060`
6. Set **Foreground** to any AI/sparkle icon you like
7. Click **Next → Finish**

This auto-generates all required mipmap-mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi PNGs.

---

## Step 3 — Build Debug APK

### Option A — Android Studio GUI
1. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Option B — Command Line
```powershell
cd "C:\Users\Ganapathy\docker-apps\maybe\meta apk"
.\gradlew.bat assembleDebug
```
APK: `app\build\outputs\apk\debug\app-debug.apk`

---

## Step 4 — Build Release APK (Signed)

### 4a. Generate a Keystore (one time only)
```powershell
keytool -genkeypair -v `
  -keystore keystore\release.jks `
  -alias metaai `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

### 4b. Configure Signing in `app/build.gradle`
Uncomment and fill in the `signingConfigs.release` block:
```groovy
signingConfigs {
    release {
        storeFile     file('keystore/release.jks')
        storePassword 'YOUR_STORE_PASSWORD'
        keyAlias      'metaai'
        keyPassword   'YOUR_KEY_PASSWORD'
    }
}
```
Also uncomment `signingConfig signingConfigs.release` inside `buildTypes.release`.

### 4c. Build
```powershell
.\gradlew.bat assembleRelease
```
Signed APK: `app\build\outputs\apk\release\app-release.apk`

---

## Step 5 — Install on Device

```powershell
# Via ADB (device connected via USB with USB Debugging enabled)
adb install app\build\outputs\apk\debug\app-debug.apk
```

Or manually copy the APK to your phone and open it.

---

## Features Implemented

| Feature | Status |
|--------|--------|
| Full-screen immersive WebView | ✅ |
| JavaScript + DOM storage | ✅ |
| Hardware acceleration | ✅ |
| Mobile Chrome user-agent | ✅ |
| Third-party cookies (for Meta/FB login) | ✅ |
| Facebook/Meta login support | ✅ |
| File upload (gallery + file manager) | ✅ |
| Camera + Microphone permissions | ✅ |
| Download to Downloads/MetaAI/ | ✅ |
| DownloadManager + notifications | ✅ |
| Runtime permission handling | ✅ |
| Back button (history / exit dialog) | ✅ |
| External links open in browser | ✅ |
| No-internet overlay + Retry | ✅ |
| Pull-to-refresh | ✅ |
| Splash screen with animation | ✅ |
| Dark mode (always dark UI) | ✅ |
| Adaptive icon (API 26+) | ✅ |
| HTTPS-only (network security config) | ✅ |
| ProGuard / R8 minification (release) | ✅ |
| Progress bar while loading | ✅ |

---

## Troubleshooting

| Problem | Fix |
|--------|-----|
| Gradle sync fails | File → Invalidate Caches → Restart |
| `gradlew.bat` not recognised | Run from `meta apk\` folder, not `app\` |
| Icons missing on < API 26 | Complete Step 2 (Image Asset generation) |
| Login redirects broken | Ensure cookies enabled (already in code) |
| Download fails silently | Check storage permission granted on device |
| Build fails with `tools` namespace error | Add `xmlns:tools` to top of themes.xml |

---

## Project Structure

```
meta apk/
├── build.gradle                   ← Project-level Gradle
├── settings.gradle
├── gradle.properties
├── gradlew.bat                    ← Windows build script
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle               ← App dependencies + build config
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/metaai/studio/
        │   ├── SplashActivity.kt  ← Animated splash screen
        │   ├── MainActivity.kt    ← WebView + all core logic
        │   └── DownloadReceiver.kt
        ├── res/
        │   ├── layout/
        │   │   ├── activity_splash.xml
        │   │   └── activity_main.xml
        │   ├── values/
        │   │   ├── strings.xml
        │   │   ├── colors.xml
        │   │   └── themes.xml
        │   ├── drawable/
        │   │   ├── splash_gradient.xml
        │   │   ├── logo_circle_bg.xml
        │   │   ├── ic_launcher_background.xml
        │   │   └── ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/
        │   │   ├── ic_launcher.xml
        │   │   └── ic_launcher_round.xml
        │   └── xml/
        │       └── network_security_config.xml
        └── assets/                ← (optional extra HTML pages)
```
