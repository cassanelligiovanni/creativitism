#!/usr/bin/env bash
# Device-level E2E for persistent grayscale mode, covering what instrumentation
# cannot: reboot persistence and boot-time service recovery, plus a full
# excluded/normal transition after the reboot.
#
# Requires: one booted emulator/device on adb, ANDROID debug + androidTest APKs built
# (./gradlew assembleDebug assembleDebugAndroidTest). Run from the repo root.
set -euo pipefail

PKG=com.creativitism.appredirector
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
DALT_ENABLED_KEY=accessibility_display_daltonizer_enabled
DALT_KEY=accessibility_display_daltonizer
EXCLUDED_APP=com.android.settings

log() { printf '\n=== %s ===\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

secure_get() { adb shell settings get secure "$1" | tr -d '\r'; }

# Poll until the device-wide grayscale state matches $1 ("gray" or "color").
await_state() {
  local want=$1 deadline=$((SECONDS + 15))
  while ((SECONDS < deadline)); do
    local enabled mode
    enabled=$(secure_get $DALT_ENABLED_KEY)
    mode=$(secure_get $DALT_KEY)
    if [[ $want == gray && $enabled == 1 && $mode == 0 ]]; then return 0; fi
    if [[ $want == color && $enabled != 1 ]]; then return 0; fi
    sleep 1
  done
  fail "device never reached '$want' (enabled=$(secure_get $DALT_ENABLED_KEY) mode=$(secure_get $DALT_KEY))"
}

await_boot() {
  adb wait-for-device
  local deadline=$((SECONDS + 180))
  while ((SECONDS < deadline)); do
    [[ $(adb shell getprop sys.boot_completed | tr -d '\r') == 1 ]] && return 0
    sleep 2
  done
  fail "device did not finish booting"
}

service_running() {
  adb shell dumpsys activity services "$PKG/.RedirectorService" | grep -q "app=ProcessRecord"
}

log "Installing APKs"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

log "Granting permissions"
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
adb shell appops set $PKG android:get_usage_stats allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

log "Enabling grayscale mode (with $EXCLUDED_APP excluded) via script helper"
adb shell am instrument -w -e scriptHelper true -e colorApps "$EXCLUDED_APP" \
  -e class "$PKG.GrayscaleScriptHelper#enableGrayscaleMode" "$RUNNER"
# `am instrument` force-stops the app process on exit, and shell cannot start a
# non-exported service directly — opening MainActivity self-heals the service.
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
sleep 2

log "Base state: device goes grayscale"
adb shell input keyevent KEYCODE_HOME
await_state gray
echo "OK: grayscale active"

log "Rebooting device"
adb reboot
await_boot
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true

log "After reboot: grayscale setting persisted"
await_state gray
echo "OK: still grayscale after reboot"

log "After reboot: monitoring service came back"
deadline=$((SECONDS + 90))
until service_running; do
  ((SECONDS < deadline)) || fail "RedirectorService not running after reboot"
  # BOOT_COMPLETED may be throttled; USER_PRESENT after unlock retries it.
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  sleep 3
done
echo "OK: service is running"

log "Excluded app opens in color"
adb shell am start -W "$EXCLUDED_APP/.Settings" >/dev/null
await_state color
echo "OK: excluded app got color"

log "Leaving excluded app restores grayscale"
adb shell input keyevent KEYCODE_HOME
await_state gray
echo "OK: back to grayscale"

log "Cleanup: disable grayscale mode"
adb shell am instrument -w -e scriptHelper true \
  -e class "$PKG.GrayscaleScriptHelper#disableGrayscaleMode" "$RUNNER"
await_state color
echo "OK: color restored"

log "ALL REBOOT E2E CHECKS PASSED"
