#!/bin/bash
# Collect filtered logcat for the thermal camera app
# Usage: ./collect_logcat.sh [duration_seconds]

PACKAGE="com.kitoptics.thermalview"
OUTPUT="logcat-$(date +%Y%m%d_%H%M%S).txt"
DURATION="${1:-30}"

echo "Collecting $DURATION seconds of logcat..."
timeout "$DURATION" adb logcat \
  -s "KitEyeCamera:V" \
  -s "UvcCamera:V" \
  -s "UsbManager:V" \
  -s "AndroidRuntime:E" \
  > "$OUTPUT" 2>&1 || true

echo "Logcat saved to: $OUTPUT"
wc -l "$OUTPUT"
