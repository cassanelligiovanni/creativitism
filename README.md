# Creativitism~

*Trade doomscrolling for creating.*

An Android digital-wellbeing app. You sort your apps and websites into two lists —
**Blocked** (what eats your time) and **Creative** (where you'd rather be). When you
open a blocked app, Creativitism covers it with a full-screen *"Take a breath"* pause:

1. A 10-second countdown runs before anything unlocks.
2. Then you choose: **Take me somewhere better** (a random Creative app or site opens)
   or **Use it for 5 minutes**.
3. When the 5 minutes expire, the pause screen returns.

Website blocking works in Chrome through an accessibility service that reads the
address bar and pauses pages matching your blocklist.

Everything stays on your device. No accounts, no analytics, no network calls.

## Design

"Glass over paper" — warm paper ground, frosted translucent panes, IBM Plex Serif,
a teal tilde as the mark. Design spec:
[docs/superpowers/specs/2026-07-22-timed-unlock-playstore-design.md](docs/superpowers/specs/2026-07-22-timed-unlock-playstore-design.md)

## Build

- Android Studio (or plain Gradle), JDK 17, compile/target SDK 35, min SDK 26.
- `local.properties` must point at your Android SDK (`sdk.dir=...`).
- Debug build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Release bundle (needs `keystore.properties`, see below): `./gradlew bundleRelease`

## Play Store

Everything needed for submission lives in [docs/playstore/](docs/playstore/):

- [SUBMISSION_GUIDE.md](docs/playstore/SUBMISSION_GUIDE.md) — step-by-step publishing
  guide, keystore setup, policy-risk notes
- [STORE_LISTING.md](docs/playstore/STORE_LISTING.md) — listing copy and asset specs
- [PRIVACY_POLICY.md](docs/playstore/PRIVACY_POLICY.md) — host this (e.g. GitHub Pages)
- [DECLARATIONS.md](docs/playstore/DECLARATIONS.md) — Play Console form answers
- `assets/` — 512×512 icon and 1024×500 feature graphic

## Permissions (and why)

| Permission | Purpose |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Detect which app is in the foreground |
| Display over other apps | Show the pause screen above a blocked app |
| Accessibility service | Read the Chrome address bar to block websites |
| `QUERY_ALL_PACKAGES` | List your installed apps so you can build your lists |
| Notifications + foreground service | Keep the monitoring service alive and visible |
