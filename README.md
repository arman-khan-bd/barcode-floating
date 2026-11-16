# Barcode Floating Reader App (Android 5.0+)

A fully functional, native Android application built in **Java** that provides a persistent **movable Floating Button** on the screen. Users can tap the floating button at any time (from within any app like Chrome, WhatsApp, Excel, Inventory systems, etc.) to scan a barcode or QR code. 

The scanned result is:
1. **Automatically copied to the system clipboard** (`ClipboardManager`).
2. **Automatically pasted into the currently selected / focused input field** (`AccessibilityService`) across any app.

---

## 🌟 Key Features

- **Java Support from Android 5.0 (API 21 / Lollipop)** up to the latest Android 14+ (API 34).
- **Movable Floating Action Button (Overlay Widget)**:
  - Draggable anywhere on the screen with touch boundaries.
  - Non-focusable (`FLAG_NOT_FOCUSABLE`), preserving keyboard state and active input focus in the background app.
  - Automatically switches between `TYPE_PHONE` (Android 5.0 - 7.1) and `TYPE_APPLICATION_OVERLAY` (Android 8.0+).
- **Instant Barcode / QR Scanning**:
  - Powered by ZXing Android Embedded (standalone, fast, works 100% offline without Google Play Services).
  - Includes flashlight / torch toggle, audio beep, and haptic vibration feedback.
  - Supported barcode formats: QR Code, UPC-A, UPC-E, EAN-8, EAN-13, Code 39, Code 93, Code 128, ITF, Codabar, Data Matrix, Aztec, PDF417.
- **Automatic Clipboard Copy**:
  - Immediately places scanned data into the Android system clipboard.
- **Smart Auto-Paste into Active Input**:
  - Employs an Android `AccessibilityService` (`BarcodePasteAccessibilityService`) that identifies the currently active / focused input node (`AccessibilityNodeInfo.FOCUS_INPUT`) and executes `ACTION_SET_TEXT` or `ACTION_PASTE`.
  - Built-in retry handler accounts for window focus restoration when the scanner closes.
- **Modern Dashboard & Interactive Testing**:
  - One-tap toggles for floating service and permissions checklist (Overlay, Camera, Accessibility).
  - Dedicated "Test Input" area to test the auto-paste workflow in real time.
  - Scan history log with one-tap copy and clear options.

---

## 🏗️ Project Architecture

```
barcode-floating/
├── build.gradle                     # Project-level Gradle build file
├── settings.gradle                  # Gradle repositories & project modules
├── gradle.properties                # AndroidX and JVM options
├── gradlew & gradlew.bat            # Gradle wrapper scripts
├── app/
│   ├── build.gradle                 # Module configuration (minSdk 21, compileSdk 34)
│   ├── proguard-rules.pro           # ProGuard / R8 rules for ZXing
│   └── src/main/
│       ├── AndroidManifest.xml      # App permissions, activities, and services
│       ├── java/com/example/barcodefloating/
│       │   ├── MainActivity.java                   # Dashboard, permission managers & test field
│       │   ├── FloatingBarcodeService.java         # Foreground Service & movable overlay widget
│       │   ├── BarcodePasteAccessibilityService.java# Accessibility service for auto-pasting
│       │   ├── ScannerActivity.java                # ZXing camera scanner with flash & feedback
│       │   ├── PermissionHelper.java               # Version-aware permission checkers
│       │   ├── ScanHistoryManager.java             # Scan history persistence
│       │   ├── ScanHistoryAdapter.java             # RecyclerView adapter for scan items
│       │   └── ScanItem.java                       # Data model for scans
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml               # Main dashboard layout
│           │   ├── activity_scanner.xml            # Custom scanner camera overlay
│           │   ├── layout_floating_button.xml      # Floating widget layout
│           │   └── item_scan_history.xml           # Scan item card layout
│           ├── drawable/                           # Icons & vector assets
│           ├── values/                             # Colors, themes, styles, and strings
│           └── xml/
│               └── accessibility_service_config.xml# Accessibility event filters
```

---

## ⚙️ How It Works

### 1. Overlay Floating Button (`FloatingBarcodeService.java`)
- The floating button is created using Android's `WindowManager`.
- On Android 8.0+ (Oreo), it uses `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`. On Android 5.0 - 7.1, it falls back to `WindowManager.LayoutParams.TYPE_PHONE`.
- The layout flag `FLAG_NOT_FOCUSABLE` ensures that touching or dragging the floating button does not take focus away from whichever app or text field was active underneath.

### 2. Barcode Scanning (`ScannerActivity.java`)
- When the floating button is tapped, `ScannerActivity` launches with high priority.
- ZXing scans the barcode from the camera stream, triggers audio/vibration feedback, saves the barcode to history, and copies the string to `ClipboardManager`.

### 3. Auto-Paste into Selected Input (`BarcodePasteAccessibilityService.java`)
- Once `ScannerActivity` completes and dismisses, the underlying app (e.g. Chrome, WhatsApp, Notes, Sheets) regains window focus.
- The `BarcodePasteAccessibilityService` detects the focused input field via:
  ```java
  AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
  ```
- It automatically inserts the text using:
  ```java
  Bundle args = new Bundle();
  args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
  focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
  ```
- If direct text insertion is not supported by the specific third-party input field, it falls back to `AccessibilityNodeInfo.ACTION_PASTE`.

---

## 🚀 How to Build & Run

### In Android Studio:
1. Open **Android Studio**.
2. Select **File > Open...** and choose the `barcode-floating` directory.
3. Allow Gradle to sync dependencies.
4. Connect an Android device (Android 5.0 Lollipop or newer) or start an emulator.
5. Click **Run > Run 'app'** (`Shift + F10`).

### Via Command Line (Gradle):
```bash
# Debug APK Build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

---

## 🤖 GitHub Actions (Automated CI/CD Build)

A complete workflow is configured in [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).

### Features:
- **Automatic Triggers**: Runs on every `push` or `pull_request` to `main` / `master`, and supports manual runs via `workflow_dispatch`.
- **Environment**: Runs on `ubuntu-latest` with **JDK 17 (Temurin)** and Gradle caching.
- **Built Artifacts**:
  - Builds `BarcodeFloating-Debug-APK` (signed with debug keystore, ready to install directly on your phone).
  - Builds `BarcodeFloating-Release-APK`.
- **Download APK from GitHub**:
  1. Go to your GitHub repository's **Actions** tab.
  2. Click on the latest workflow run.
  3. Scroll down to the **Artifacts** section and download `BarcodeFloating-Debug-APK.zip`.
- **Automatic GitHub Releases**: When you push a git tag starting with `v` (e.g. `v1.0.0`), it automatically creates a GitHub Release with the APK files attached.

---

## 📱 Permissions Required

1. **Camera (`android.permission.CAMERA`)**: To capture camera feed for reading barcodes.
2. **Display over other apps (`android.permission.SYSTEM_ALERT_WINDOW`)**: To draw the floating button widget over any running application. (Granted automatically at install time on Android 5.0 - 5.1; requested via runtime settings on Android 6.0+).
3. **Accessibility Service (`Barcode Auto-Paste Service`)**: To detect the active input field across any app and automatically paste text.
