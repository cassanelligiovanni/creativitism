# Design: Timed Unlock, UI Overhaul & Play Store Readiness

Date: 2026-07-22
App: Creativitism (com.creativitism.appredirector) — blocks "soul-sucking" apps/sites and redirects to creativity-boosting ones.

## 1. Timed-unlock flow (blocked-app interstitial)

### Current behavior
`RedirectorService` polls the foreground app every 500 ms. When a blocked app comes to
the foreground it immediately launches `RedirectProxyActivity`, which opens a random
creativity-boosting app/URL.

### New behavior
When a blocked app comes to the foreground, the service launches a full-screen
**`BlockedInterstitialActivity`** over it instead of redirecting immediately:

1. A 10-second breathing countdown runs (circular progress). During it the user can
   only press **"Take me somewhere better"** (immediate redirect to a creative item)
   or go back/home.
2. When the countdown finishes, **"Use it for 5 minutes"** becomes enabled. Pressing it
   grants a temporary allowance for that package and finishes the interstitial,
   dropping the user back into the blocked app.
3. While an allowance is active the service does not block that package.
4. When the allowance expires and the blocked app is still in the foreground, the
   service re-launches the interstitial (the "kick out"): the user is out of the app
   and must either redirect or wait another 10 s for another 5 minutes.

### Components
- **`TemporaryAllowanceManager`** — stores `package → expiryEpochMillis` in
  SharedPreferences (`temporary_allowances`). API: `grant(pkg, durationMs, now)`,
  `isAllowed(pkg, now)`, `expiredJustNow` handling is the service's job. The pure
  decision logic lives in **`AllowancePolicy`** (plain Kotlin object) so it is unit
  testable on the JVM without Android.
- **`BlockedInterstitialActivity`** — full-screen, receives `BLOCKED_PACKAGE`.
  Uses `CountDownTimer`. On "Use it for 5 minutes": grant allowance → relaunch the
  blocked app → finish. On "Take me somewhere better": pick a random creative item →
  route through the existing `RedirectProxyActivity` logic → finish.
  `onBackPressed`/home just finishes (user leaves the blocked app anyway; next
  foreground detection re-triggers the interstitial).
- **`RedirectorService` changes** — each tick:
  - Ignore our own packages (interstitial/main/proxy) so the overlay doesn't trigger loops.
  - If the foreground app is blocked and **not** allowed → launch the interstitial,
    but at most once per 3 s per package (debounce) so the 500 ms poll doesn't stack
    multiple activities while the interstitial itself is animating in.
  - The "app changed" guard is removed for the block check (needed for expiry kick-out
    while the user stays inside the app); the debounce replaces it.

Constants: `WAIT_SECONDS = 10`, `ALLOWANCE_MINUTES = 5` (single source in
`TemporaryAllowanceManager`). Websites (accessibility URL interception) keep the
instant-redirect behavior — the request covers apps.

## 2. UI/UX overhaul — "glass over paper"

Direction agreed interactively with Giovanni on 2026-07-22 (first indigo/amber
proposal rejected). References: thesephist.com (literary paper + IBM Plex Serif)
mixed with Apple Liquid Glass (frosted panes). Stack unchanged (Views + M3 +
viewBinding).

- **Rebrand** to **Creativitism** — tagline "trade doomscrolling for creating".
- **Tokens** (light / dark "ink glass"): paper ground #F4F0E7→#E0D8C4 with teal +
  apricot radial washes / #2B2933→#17151C; ink #26241D / #EDEAE3; muted italic
  #7D7666 / #8D889C; teal accent #0B8A7D / #3FD2C2 (creative); burnt sienna
  #B4552D / #E07A54 (blocked). Tokens are `cv_*` colors, dark via values-night.
- **Surfaces**: frosted panes — translucent white linear gradient + 1dp specular
  white stroke, 22dp radius (`bg_glass.xml`), over `bg_paper.xml` layered washes.
- **Type**: IBM Plex Serif (bundled, OFL, res/font) for brand/titles/subtitles
  (subtitles italic); system sans for list rows and buttons.
- **MainActivity**: serif wordmark "Creativitism~" (teal tilde), protection pane
  with switch + italic status line, Blocked/Creative panes with sienna block icon
  and teal tilde icon, empty-state hints, search filter over the installed apps.
- **Interstitial**: frosted sheet (`bg_frost`, translucent activity so the blocked
  app is faintly visible), big teal serif tilde, "Take a breath.", teal countdown
  ring, ink pill "Take me somewhere better", ghost pill "Use it for 5 minutes".

## 3. Logo & store graphics

- **Mark**: a tilde (~) under glass — frosted rounded pane over warm paper with
  teal/apricot washes, teal serif tilde.
- Adaptive launcher icon: layered-wash background + frosted-pane/tilde vector
  foreground + white tilde monochrome layer (themed icons).
- 512×512 store icon and 1024×500 feature graphic rendered from HTML mockups via
  headless browser screenshots (docs/playstore/assets/).

## 4. Play Store readiness

- **Build**: AGP 8.2.1 → 8.7.3 (Gradle 8.9 already present), compileSdk/targetSdk 35
  (required for new apps in 2026), minSdk 26 (simplifies adaptive icons; Android 8.0+
  covers ~97% of devices), version 1.0 (code 1), `minifyEnabled false` for the first
  release (no reflection-sensitive risk), signing via `keystore.properties`
  (git-ignored) with a helper script to generate the upload keystore.
- **Manifest fixes**: foreground service type `dataSync` → `specialUse` with
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation (app-blocking/digital-wellbeing);
  remove `BIND_JOB_SERVICE` permission on the service (wrong and harmful).
- **Policy compliance**:
  - Prominent in-app disclosure dialog before sending the user to Accessibility
    settings (Play's AccessibilityService API policy).
  - Privacy policy (hostable on GitHub Pages, free) — required for
    PACKAGE_USAGE_STATS + Accessibility.
  - Declaration-form answers doc (QUERY_ALL_PACKAGES, Usage Stats, Accessibility)
    and Data safety form answers.
- **Docs**: `docs/playstore/` — submission guide (incl. one-time $25 developer fee,
  closed-testing requirement for new personal accounts), listing text, declarations.

## Testing
- JVM unit tests for `AllowancePolicy` (grant/active/expiry boundaries) and the
  existing matching heuristic in `RedirectionManager` (extracted to a pure function).
- Manual: build `assembleDebug`; verification of the full flow needs a device/emulator.
