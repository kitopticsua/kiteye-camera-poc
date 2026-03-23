#!/bin/bash
# KitEye Camera Benchmark — runs app and collects FPS/memory metrics via ADB
# Usage: ./benchmark_run.sh [duration_seconds]

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
echo "Android: $(adb shell getprop ro.build.version.release)" | tee -a "$RESULT_FILE"
echo "Timestamp: $TIMESTAMP" | tee -a "$RESULT_FILE"
echo "" | tee -a "$RESULT_FILE"

# Launch the app
adb shell am start -n "$PACKAGE/.app.MainActivity"
sleep 3

echo "=== Memory & CPU ===" | tee -a "$RESULT_FILE"

for i in $(seq 1 $((DURATION / 5))); do
  sleep 5
  MEM=$(adb shell dumpsys meminfo "$PACKAGE" | grep "TOTAL PSS" | awk '{print $3}')
  echo "t=$((i*5))s PSS=${MEM}KB" | tee -a "$RESULT_FILE"
done

echo "" | tee -a "$RESULT_FILE"
echo "=== Frame Stats ===" | tee -a "$RESULT_FILE"

adb shell dumpsys gfxinfo "$PACKAGE" reset > /dev/null
sleep 10
adb shell dumpsys gfxinfo "$PACKAGE" framestats | grep -E "Total frames|Janky frames|50th|90th|95th|99th" | tee -a "$RESULT_FILE"

echo "" | tee -a "$RESULT_FILE"
echo "=== Battery ===" | tee -a "$RESULT_FILE"
adb shell dumpsys batterystats --charged "$PACKAGE" | grep -E "Foreground|CPU|Wake lock" | head -20 | tee -a "$RESULT_FILE"

adb shell am force-stop "$PACKAGE"

echo "" | tee -a "$RESULT_FILE"
echo "=== Benchmark Complete ===" | tee -a "$RESULT_FILE"
echo "Results: $RESULT_FILE"
