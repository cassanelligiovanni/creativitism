# Persistent Grayscale Mode — design & plan (2026-08-23)

## Goal

The device is grayscale by default, persistently. The user picks the few apps that are
allowed to stay in color. No sessions, no timers, no unlock flows.

## Mechanism

System-wide grayscale is applied through Android's **color correction** (daltonizer)
secure settings:

- `accessibility_display_daltonizer_enabled` — 0/1
- `accessibility_display_daltonizer` — `0` = monochromacy (grayscale); other values are
  color-blindness correction modes; disabled default reads as `-1`.

These keys exist since API 21 (minSdk here is 26) and drive the same surface-flinger
color transform the system Settings "Color correction → Grayscale" toggle uses, so the
result is genuinely system-wide (every app, launcher, system UI).

Writing them requires `android.permission.WRITE_SECURE_SETTINGS`. Its protection level
includes `development`, which means it **can be granted to a normal app once via ADB**:

    adb shell pm grant com.creativitism.appredirector android.permission.WRITE_SECURE_SETTINGS

This is the standard approach for non-system digital-wellbeing apps; there is no
overlay- or accessibility-API alternative that recolors other apps' pixels. The app
detects the permission, walks the user through the one-time grant, degrades gracefully
without it, and auto-recovers when it is granted/restored.

### Respecting existing color correction

Some users run color correction for actual color-blindness. When grayscale mode takes
control it snapshots the pre-existing daltonizer values; "color" (excluded app in
foreground) restores that snapshot rather than blindly disabling correction. A snapshot
that is itself grayscale is normalized to "disabled" (otherwise exclusions could never
show color). Turning the mode off restores the snapshot and clears it.

## Architecture (reuses existing systems)

- **`GrayscaleStore`** — SharedPreferences-backed (same pattern as `AppSettingsStore`,
  testable with `FakeSharedPreferences`): `enabled`, `colorApps: Set<String>`,
  daltonizer snapshot.
- **`GrayscalePolicy`** — pure decision logic: `(enabled, screenInteractive,
  foregroundPackage, colorApps) -> grayscale? / don't-touch`. Screen off ⇒ grayscale
  (lock screen follows the base state). Unknown foreground ⇒ grayscale. Creativitism
  itself follows the same rule as any app — no special cases.
- **`GrayscaleController`** — applies desired state to Settings.Secure. Skips redundant
  writes (in-memory last-applied + periodic 10s reconciliation against the actual
  setting so external changes don't silently stick). Catches `SecurityException`
  (permission revoked mid-run) without crashing the loop.
- **`RedirectorService`** — the existing 500 ms foreground poll calls the controller
  every tick; enforcement also runs once at service start (boot / restart recovery).
  No second monitoring mechanism.
- **Service lifecycle** — every "should the service run?" check (`onDestroy`,
  `onTaskRemoved`, `ServiceKeepAliveManager`, `ServiceRestartReceiver`,
  `MainActivity.stopRedirectorService`) now considers `service_enabled OR grayscale
  enabled`. Blocking behavior itself stays gated on `service_enabled`, so
  grayscale-only users get monitoring without blocking.
- **UI** — a new "Grayscale" glass card on the Guard tab (switch + status + "Apps in
  color" row + one-time setup button), and a dedicated `ColorAppsActivity` (searchable
  list of installed apps, tap to toggle color exclusion, persisted immediately; the
  service loop picks changes up within 500 ms).

## Lifecycle matrix

| Event | Behavior |
|---|---|
| Reboot | daltonizer setting itself persists; boot receiver restarts the service which re-enforces base state |
| Service killed/restarted | keep-alive alarm restarts it; `onStartCommand` re-enforces |
| Screen off/lock | policy says grayscale (base state) |
| Exclusion list edited while active | loop re-reads store every tick; ≤500 ms to take effect |
| Permission revoked | writes fail safe, status surfaces "setup needed", no crash; setting left as-is |
| Permission re-granted | next tick enforces again automatically |
| App uninstalled while gray | Android keeps the last daltonizer value — user restores via Settings → Accessibility → Color correction (documented limitation shared by all such apps) |

## Testing

- **Unit (JVM)** — policy decision table, store persistence/snapshot normalization.
- **Instrumentation E2E (emulator, real system settings)** — grant
  `WRITE_SECURE_SETTINGS` + usage access via `UiAutomation.executeShellCommand`; enable
  mode; launch a real second app (Android Settings); assert the actual
  `Settings.Secure` daltonizer values flip: normal→excluded→normal transitions,
  multiple exclusions, add/remove while active, service restart, permission
  revoke/restore.
- **Script E2E** (`scripts/e2e_grayscale.sh`) — full-device flow including
  `adb reboot` persistence, which instrumentation cannot survive.

## Out of scope / rejected

- Overlay-based "grayscale" (impossible: overlays cannot recolor pixels beneath them).
- Shizuku/root integrations (unnecessary dependency).
- Temporary sessions, timers, per-launch unlocks (explicitly excluded by spec).
