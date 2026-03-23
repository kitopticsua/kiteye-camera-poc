# Android UVC Thermal Camera PoC — CI/CD & Infrastructure

**Expert:** DevOps Engineer
**Date:** 2026-03-23

---

## 1. Repository Structure

```
kiteye-camera-poc/
├── .github/
│   ├── workflows/
│   │   ├── pr-check.yml
│   │   ├── release-build.yml
│   │   └── device-benchmark.yml
│   └── PULL_REQUEST_TEMPLATE.md
├── app/
│   ├── src/
│   │   ├── main/java/com/kitoptics/thermalview/
│   │   ├── test/                    # JVM unit tests
│   │   └── androidTest/             # instrumented tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── scripts/
│   ├── benchmark_run.sh
│   ├── install_debug.sh
│   └── collect_logcat.sh
├── docs/
│   ├── experts-board/
│   └── ADR-*.md
├── .claude/
│   ├── sprint.md
│   └── stories/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── CLAUDE.md
└── .gitignore
```

---

## 2. CI/CD Pipeline Design

### 2.1 PR Check Workflow

**File: `.github/workflows/pr-check.yml`**

```yaml
name: PR Check

on:
  pull_request:
    branches: [ main, develop ]
  push:
    branches: [ develop ]

concurrency:
  group: pr-check-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    name: Lint & Static Analysis
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew ktlintCheck
      - run: ./gradlew lintDebug
      - name: Upload Lint Reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: lint-reports-${{ github.run_number }}
          path: app/build/reports/lint-results-debug.html

  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew testDebugUnitTest
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results-${{ github.run_number }}
          path: app/build/reports/tests/testDebugUnitTest/

  build-debug-apk:
    name: Build Debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 20
    needs: [lint, unit-tests]
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew assembleDebug
      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk-${{ github.run_number }}
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 7
```

### 2.2 Release Build Workflow

**File: `.github/workflows/release-build.yml`**

```yaml
name: Release Build

on:
  push:
    tags: ['v*.*.*']
  workflow_dispatch:
    inputs:
      version_name:
        description: 'Version name (e.g. 0.1.0)'
        required: true

jobs:
  build-release:
    name: Build & Sign Release APK
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - run: chmod +x gradlew
      - name: Decode keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > app/kiteye-release.keystore
      - name: Build release APK
        env:
          KEYSTORE_PATH: kiteye-release.keystore
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
        run: ./gradlew assembleRelease
      - name: Upload release APK
        uses: actions/upload-artifact@v4
        with:
          name: release-apk
          path: app/build/outputs/apk/release/app-release.apk
          retention-days: 90
      - name: Create GitHub Release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/app-release.apk
          generate_release_notes: true
      - name: Cleanup keystore
        if: always()
        run: rm -f app/kiteye-release.keystore
```

**Secrets needed:** `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`

### 2.3 Device Benchmark Workflow (Self-Hosted)

```yaml
name: Device Benchmark

on:
  workflow_dispatch:
    inputs:
      test_duration_seconds:
        description: 'Duration in seconds'
        default: '60'

jobs:
  benchmark:
    runs-on: [self-hosted, android-device]
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - name: Check device
        run: |
          adb devices
          echo "Device: $(adb shell getprop ro.product.model)"
      - run: ./gradlew assembleDebug
      - run: adb install -r app/build/outputs/apk/debug/app-debug.apk
      - run: chmod +x scripts/benchmark_run.sh && ./scripts/benchmark_run.sh ${{ github.event.inputs.test_duration_seconds }}
      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: benchmark-results-${{ github.run_number }}
          path: benchmark-results/
```

---

## 3. Testing Infrastructure

### Decision Matrix

| Approach | UVC Camera | Automated | Decision |
|---|---|---|---|
| Firebase Test Lab | NO — USB blocked | Yes | REJECTED |
| AWS Device Farm | NO — USB blocked | Yes | REJECTED |
| Emulator (AVD) | NO — no USB | Yes | REJECTED for camera |
| Self-hosted runner | YES | Yes | **CHOSEN** |
| Manual ADB over WiFi | YES | Semi | **CHOSEN for dev** |

### Self-Hosted Runner Setup

```bash
# 1. Install ADB
sudo apt-get install android-tools-adb

# 2. Install GitHub Actions runner
mkdir -p ~/actions-runner && cd ~/actions-runner
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/latest/download/actions-runner-linux-x64-2.314.1.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/kitopticsua/kiteye-camera-poc --labels android-device
sudo ./svc.sh install && sudo ./svc.sh start
```

### Sentry Integration

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.sentry:sentry-android:7.10.0")
}
```

```xml
<!-- AndroidManifest.xml -->
<meta-data android:name="io.sentry.dsn" android:value="${SENTRY_DSN}" />
<meta-data android:name="io.sentry.traces-sample-rate" android:value="1.0" />
```

---

## 4. Build Configuration

```kotlin
android {
    namespace = "com.kitoptics.thermalview"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kitoptics.thermalview"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

---

## 5. Branching Strategy

```
main          ──●────●──────●──── (protected, tags = releases)
                 ↑    ↑      ↑
develop       ──●──●──●──●───●──── (integration)
                    ↑     ↑
feature/        ●──●      ●──●    (short-lived, <2 days)
```

- Feature branches: `feature/CAM-N-description` or `fix/CAM-N-description`
- `main` ← `develop` via PR, requires CI green
- Tags: `v0.1.0` → first preview, `v0.2.0` → recording, `v1.0.0` → PoC complete

---

## 6. Benchmark Script

**File: `scripts/benchmark_run.sh`**

```bash
#!/bin/bash
set -euo pipefail
DURATION="${1:-60}"
PACKAGE="com.kitoptics.thermalview"
RESULTS_DIR="benchmark-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULT_FILE="$RESULTS_DIR/benchmark_$TIMESTAMP.txt"

mkdir -p "$RESULTS_DIR"
echo "=== KitEye Camera Benchmark ===" | tee "$RESULT_FILE"
echo "Duration: ${DURATION}s" | tee -a "$RESULT_FILE"
echo "Device: $(adb shell getprop ro.product.model)" | tee -a "$RESULT_FILE"

adb shell am start -n "$PACKAGE/.app.MainActivity"
sleep 3

for i in $(seq 1 $((DURATION / 5))); do
  sleep 5
  MEM=$(adb shell dumpsys meminfo "$PACKAGE" | grep "TOTAL PSS" | awk '{print $3}')
  echo "t=$((i*5))s PSS=${MEM}KB" | tee -a "$RESULT_FILE"
done

adb shell dumpsys gfxinfo "$PACKAGE" framestats | tee -a "$RESULT_FILE"
adb shell am force-stop "$PACKAGE"
echo "=== Complete ===" | tee -a "$RESULT_FILE"
```

---

## 7. Keystore Setup

```bash
keytool -genkey -v \
  -keystore kiteye-poc-release.keystore \
  -alias kiteye-poc \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=KitOptics, OU=KitEye, O=KitOptics UA, L=Kyiv, C=UA"

# Encode for GitHub secret
base64 -w 0 kiteye-poc-release.keystore | pbcopy
# Paste as KEYSTORE_BASE64 secret
```
