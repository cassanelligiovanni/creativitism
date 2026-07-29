# Fire-Alarm Creativitism Backport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Creativitism’s calm pause with a fire-alarm intervention, add per-item enforcement/confession, active hours, local stats/receipt, and Raycast dark UI — keeping lists, permissions, and on-device privacy.

**Architecture:** Evolve in place on Views/XML. Pure JVM policy objects (`*Policy`, `StatsAggregator`, `ConfessionNormalizer`) own decisions and are unit-tested; thin SharedPreferences stores wrap them; `RedirectorService` / `UrlInterceptorService` launch `AlarmActivity` instead of the old interstitial / instant redirect.

**Tech Stack:** Kotlin, Android Views + ViewBinding, Material 3, SharedPreferences, UsageStats FGS, AccessibilityService (Chrome), SpeechRecognizer, JUnit4 JVM tests, Gradle AGP 8.7 / compileSdk 35 / minSdk 26.

**Spec:** `docs/superpowers/specs/2026-07-29-fire-alarm-backport-design.md`

**Plan revisions (2026-07-29):** Incorporates findings from `cursor-plan-review-2026-07-29-125542.md` (bought-time close-out, Chrome return, mid-alarm dormancy, Task 10 atomic cutover, VisitTracker enumeration, logging sites, FileProvider + backup exclusions, clock/sentinel/speech gating).

## Global Constraints

- App name stays **Creativitism~**; tagline *"trade doomscrolling for creating"*
- Countdown **10s**; strict allowance **5 min**; visit end after **5 min** continuous absence
- Defaults: enforcement **STRICT**, confession **on**
- Flash ≤ **2.5 Hz**; alarm audio on **USAGE_ALARM**; if inaudible → aggressive vibration
- No Compose migration; no network/analytics/accounts
- Domain matching unchanged (`BlockMatcher`)
- Chrome-only website interception
- Package `com.creativitism.appredirector`; keep existing permission disclosure style
- Orchestrator verifies each milestone on emulator before continuing
- Composer 2.5 may take UI/theme/docs/icon slices; orchestrator owns service/policy/alarm lifecycle
- **Clocks:** Persist and compare visit/allowance/log timestamps with `System.currentTimeMillis()` only. Debounce maps that are in-memory may use `elapsedRealtime`, but never mix a persisted wall-clock value with elapsedRealtime.
- **Logging sites:** `ALARM_FIRED` only in `AlarmActivity.onCreate`/`handleIntent` (once per show). Give-in logs both `GAVE_IN` and `ALLOWANCE_GRANTED`. Never double-log the same alarm.
- **Backup:** New prefs (`intervention_log`, `blocked_item_policy`, `active_hours`, `app_settings`, `temporary_allowances`, `visit_tracker`, and `redirections_v2`) must be excluded from Auto Backup / data extraction — update both XML rule files (and fix the stale `redirections.xml` exclusion).

---

## File structure

### Create

| File | Responsibility |
|---|---|
| `app/src/main/java/.../EnforcementMode.kt` | Enum `STRICT`, `ONCE_PER_VISIT` |
| `app/src/main/java/.../BlockedItemPolicy.kt` | Data class + defaults |
| `app/src/main/java/.../BlockedItemPolicyStore.kt` | Prefs CRUD per item id |
| `app/src/main/java/.../VisitPolicy.kt` | Pure visit-end / refresh rules |
| `app/src/main/java/.../VisitTracker.kt` | Prefs last-seen + open visits |
| `app/src/main/java/.../SchedulePolicy.kt` | Pure armed/dormant for clock |
| `app/src/main/java/.../ScheduleStore.kt` | Prefs for active hours |
| `app/src/main/java/.../AppSettingsStore.kt` | resume_last_work, last_creative_item, last receipt week |
| `app/src/main/java/.../InterventionEvent.kt` | Event kinds + data class |
| `app/src/main/java/.../InterventionLog.kt` | Prefs append + 90-day prune |
| `app/src/main/java/.../StatsAggregator.kt` | Reclaimed, bought, streak, offenders |
| `app/src/main/java/.../ConfessionNormalizer.kt` | String normalize + match |
| `app/src/main/java/.../ConfessionSentence.kt` | Build “I choose X over Y.” |
| `app/src/main/java/.../AlarmAudioController.kt` | Klaxon + vibe + mute fallback |
| `app/src/main/java/.../AlarmActivity.kt` | Fire-alarm UI (replaces interstitial) |
| `app/src/main/java/.../ConfessionSpeechHelper.kt` | SpeechRecognizer lifecycle (required, not optional) |
| `app/src/main/java/.../WeeklyReceiptHelper.kt` | Receipt copy + share bitmap via FileProvider |
| `app/src/main/res/layout/activity_alarm.xml` | Alarm layout |
| `app/src/main/res/layout/item_blocked_row.xml` | Blocked row with mode + confession (new layout; creative rows keep `item_managed_item.xml`) |
| `app/src/main/res/layout/section_stats.xml` | Stats block included from main |
| `app/src/main/res/raw/alarm_klaxon.wav` | Short loopable klaxon |
| `app/src/main/res/font/inter_*.ttf` + `inter.xml` | Bundle Inter TTFs in-repo (no downloadable-fonts network dependency) |
| `app/src/main/res/xml/file_paths.xml` | FileProvider paths for receipt share cache |
| `app/src/test/java/.../*PolicyTest.kt`, `StatsAggregatorTest.kt`, `ConfessionNormalizerTest.kt`, `VisitPolicyTest.kt`, `SchedulePolicyTest.kt` | JVM tests |

### Modify

| File | Change |
|---|---|
| `AllowancePolicy.kt` | Add visit-scoped sentinel helpers |
| `TemporaryAllowanceManager.kt` | Strict + visit grants; **special-case `VISIT_SCOPED` before expiry cleanup** |
| `RedirectionManager.kt` | Escape target helper; on remove blocked → policy + allowance + visit cleanup |
| `RedirectorService.kt` | Schedule, visits, allowances, launch AlarmActivity, notification armed/dormant, mid-alarm dormancy signal |
| `UrlInterceptorService.kt` | Launch AlarmActivity; respect schedule/allowance; end domain visit on navigate-away |
| `MainActivity.kt` | Theme, schedule UI, stats, resume setting, receipt |
| `ManagedItemsAdapter.kt` | Blocked row controls via `item_blocked_row.xml` |
| `CreativitismApp.kt` | Force dark / night yes |
| `AndroidManifest.xml` | AlarmActivity (singleTask/affinity/excludeFromRecents/opaque theme/configChanges), RECORD_AUDIO, VIBRATE, FileProvider |
| `backup_rules.xml`, `data_extraction_rules.xml` | Exclude all Creativitism prefs incl. `redirections_v2.xml` |
| `colors.xml`, `themes.xml`, `strings.xml`, layouts, icons | Raycast restyle; delete orphaned paper/glass/frost tokens |
| Store docs under `docs/playstore/`, `docs/privacy/` | Alarm, mic, stats, no-cloud backup stance |

### Delete / retire in Task 10 (same commit as service rewire)

- `BlockedInterstitialActivity.kt`, `activity_blocked_interstitial.xml`, translucent interstitial theme once opaque alarm theme exists
- Paper/glass drawables unused after restyle (Task 20 cleanup pass)

---

### Task 1: EnforcementMode + BlockedItemPolicy defaults

**Files:**
- Create: `app/src/main/java/com/creativitism/appredirector/EnforcementMode.kt`
- Create: `app/src/main/java/com/creativitism/appredirector/BlockedItemPolicy.kt`
- Create: `app/src/test/java/com/creativitism/appredirector/BlockedItemPolicyTest.kt`

**Interfaces:**
- Produces: `enum class EnforcementMode { STRICT, ONCE_PER_VISIT }`; `data class BlockedItemPolicy(val enforcementMode: EnforcementMode, val confessionEnabled: Boolean)` with `companion object { val DEFAULT = BlockedItemPolicy(STRICT, true) }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedItemPolicyTest {
    @Test
    fun `defaults are strict with confession on`() {
        val d = BlockedItemPolicy.DEFAULT
        assertEquals(EnforcementMode.STRICT, d.enforcementMode)
        assertTrue(d.confessionEnabled)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.creativitism.appredirector.BlockedItemPolicyTest`

Expected: FAIL (classes missing)

- [ ] **Step 3: Write minimal implementation**

```kotlin
enum class EnforcementMode { STRICT, ONCE_PER_VISIT }

data class BlockedItemPolicy(
    val enforcementMode: EnforcementMode,
    val confessionEnabled: Boolean,
) {
    companion object {
        val DEFAULT = BlockedItemPolicy(
            enforcementMode = EnforcementMode.STRICT,
            confessionEnabled = true,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.creativitism.appredirector.BlockedItemPolicyTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/creativitism/appredirector/EnforcementMode.kt \
  app/src/main/java/com/creativitism/appredirector/BlockedItemPolicy.kt \
  app/src/test/java/com/creativitism/appredirector/BlockedItemPolicyTest.kt
git commit -m "Add blocked-item policy defaults (strict, confession on)."
```

---

### Task 2: BlockedItemPolicyStore

**Files:**
- Create: `app/src/main/java/com/creativitism/appredirector/BlockedItemPolicyStore.kt`
- Test: exercise via thin logic — prefer extracting encode/decode to testable functions in the same file or `BlockedItemPolicyCodec` object

**Interfaces:**
- Consumes: `BlockedItemPolicy`, `EnforcementMode`
- Produces:
  - `fun get(itemId: String): BlockedItemPolicy`
  - `fun set(itemId: String, policy: BlockedItemPolicy)`
  - `fun remove(itemId: String)`
  - Prefs file name: `"blocked_item_policy"`
  - Storage format per key: `"STRICT|1"` / `"ONCE_PER_VISIT|0"` (mode + confession 0/1)

- [ ] **Step 1: Write failing codec tests**

```kotlin
class BlockedItemPolicyCodecTest {
    @Test
    fun `parse missing yields default`() {
        assertEquals(BlockedItemPolicy.DEFAULT, BlockedItemPolicyCodec.decode(null))
    }

    @Test
    fun `round trip once-per-visit confession off`() {
        val p = BlockedItemPolicy(EnforcementMode.ONCE_PER_VISIT, false)
        assertEquals(p, BlockedItemPolicyCodec.decode(BlockedItemPolicyCodec.encode(p)))
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests com.creativitism.appredirector.BlockedItemPolicyCodecTest`

- [ ] **Step 3: Implement codec + store**

```kotlin
object BlockedItemPolicyCodec {
    fun encode(p: BlockedItemPolicy): String =
        "${p.enforcementMode.name}|${if (p.confessionEnabled) 1 else 0}"

    fun decode(raw: String?): BlockedItemPolicy {
        if (raw.isNullOrBlank()) return BlockedItemPolicy.DEFAULT
        val parts = raw.split("|")
        val mode = runCatching { EnforcementMode.valueOf(parts[0]) }
            .getOrDefault(EnforcementMode.STRICT)
        val confession = parts.getOrNull(1) != "0"
        return BlockedItemPolicy(mode, confession)
    }
}

class BlockedItemPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("blocked_item_policy", Context.MODE_PRIVATE)
    fun get(itemId: String) = BlockedItemPolicyCodec.decode(prefs.getString(itemId, null))
    fun set(itemId: String, policy: BlockedItemPolicy) {
        prefs.edit().putString(itemId, BlockedItemPolicyCodec.encode(policy)).apply()
    }
    fun remove(itemId: String) { prefs.edit().remove(itemId).apply() }
}
```

- [ ] **Step 4: Run tests — PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "Add BlockedItemPolicyStore persistence."
```

---

### Task 3: VisitPolicy (pure)

**Files:**
- Create: `app/src/main/java/com/creativitism/appredirector/VisitPolicy.kt`
- Create: `app/src/test/java/com/creativitism/appredirector/VisitPolicyTest.kt`

**Interfaces:**
- Produces:
  - `const val VISIT_END_AFTER_MILLIS = 5 * 60_000L`
  - `fun shouldEndVisit(lastSeenAt: Long, now: Long): Boolean` → `now - lastSeenAt >= VISIT_END_AFTER_MILLIS`
  - `fun isWithinVisit(lastSeenAt: Long, now: Long): Boolean` → `!shouldEndVisit(...)`

- [ ] **Step 1: Failing tests** — assert 5 min boundary (still within at 5min−1, ends at exactly 5min)

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement `VisitPolicy`**

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit** — `Add VisitPolicy five-minute visit end rule.`

---

### Task 4: Extend AllowancePolicy for visit-scoped grants

**Files:**
- Modify: `app/src/main/java/com/creativitism/appredirector/AllowancePolicy.kt`
- Modify: `app/src/test/java/com/creativitism/appredirector/AllowancePolicyTest.kt`
- Modify: `app/src/main/java/com/creativitism/appredirector/TemporaryAllowanceManager.kt`

**Interfaces:**
- Produces additions:
  - `const val VISIT_SCOPED: Long = -1L` (sentinel stored as expiry)
  - `fun isVisitScoped(expiryMillis: Long): Boolean` → `expiryMillis == VISIT_SCOPED`
  - `fun isAllowed(expiryMillis: Long, now: Long, visitStillOpen: Boolean): Boolean`  
    — if visit-scoped → `visitStillOpen`; else existing timed check (`expiry != NO_ALLOWANCE && now < expiry`)
- `TemporaryAllowanceManager` **must** special-case the sentinel **before** any “expired → remove” cleanup:
  ```kotlin
  fun isAllowed(itemId: String, now: Long, visitStillOpen: Boolean): Boolean {
      val expiry = prefs.getLong(itemId, AllowancePolicy.NO_ALLOWANCE)
      if (AllowancePolicy.isVisitScoped(expiry)) {
          if (!visitStillOpen) {
              prefs.edit().remove(itemId).apply()
              return false
          }
          return true
      }
      if (expiry != AllowancePolicy.NO_ALLOWANCE && !AllowancePolicy.isAllowed(expiry, now)) {
          prefs.edit().remove(itemId).apply()
      }
      return AllowancePolicy.isAllowed(expiry, now)
  }
  ```
- Also: `grantStrict(itemId, now)`, `grantVisit(itemId)` → store `VISIT_SCOPED`, `clear(itemId)`, keep `grant` as alias to `grantStrict`
- Unit test: visit-scoped entry is **not** deleted on `isAllowed(..., visitStillOpen=true)` when `now` is arbitrary

- [ ] **Step 1: Extend unit tests for visit-scoped allowed/denied + cleanup-does-not-wipe-sentinel**

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement policy + manager methods with sentinel special-case**

- [ ] **Step 4: Run all allowance tests — PASS**

- [ ] **Step 5: Commit** — `Support visit-scoped temporary allowances.`

---

### Task 5: SchedulePolicy (pure)

**Files:**
- Create: `app/src/main/java/com/creativitism/appredirector/SchedulePolicy.kt`
- Create: `app/src/test/java/com/creativitism/appredirector/SchedulePolicyTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class ActiveHours(
    val enabled: Boolean,
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int,
    val weekdaysBitmask: Int, // bit0=Mon … bit6=Sun; 0 = all days
)

object SchedulePolicy {
    fun isArmed(schedule: ActiveHours, minuteOfDay: Int, weekdayMonday0: Int): Boolean
    fun nextResumeLabel(schedule: ActiveHours, minuteOfDay: Int, weekdayMonday0: Int): String?
}
```
- Rules: `!enabled` → always armed. If bitmask 0 or day bit set, and time in window: handle overnight windows (`start > end` means spans midnight). Otherwise dormant.

- [ ] **Step 1: Tests** — disabled=always armed; 09:00–18:00 Mon armed at 10:00; dormant at 20:00; overnight 22:00–06:00 armed at 23:00 and 01:00

- [ ] **Step 2: FAIL → Step 3: implement → Step 4: PASS → Step 5: Commit** — `Add SchedulePolicy for active hours.`

---

### Task 6: ScheduleStore + AppSettingsStore

**Files:**
- Create: `app/src/main/java/com/creativitism/appredirector/ScheduleStore.kt`
- Create: `app/src/main/java/com/creativitism/appredirector/AppSettingsStore.kt`

**Interfaces:**
- `ScheduleStore`: load/save `ActiveHours` in prefs `"active_hours"` (defaults `enabled=false`)
- `AppSettingsStore` prefs `"app_settings"`:
  - `resumeLastWork: Boolean` default false
  - `lastCreativeItem: String?`
  - `lastReceiptWeekId: String?` (e.g. `"2026-W31"` ISO week)

- [ ] **Step 1: Implement stores (no Android instrumented test required; keep getters/setters trivial)**

- [ ] **Step 2: Commit** — `Add ScheduleStore and AppSettingsStore.`

---

### Task 7: InterventionLog + StatsAggregator

**Files:**
- Create: `InterventionEvent.kt`, `InterventionLog.kt`, `StatsAggregator.kt`
- Create: `app/src/test/java/.../StatsAggregatorTest.kt`

**Interfaces:**
```kotlin
enum class InterventionKind {
    ALARM_FIRED, ESCAPED, GAVE_IN, ALLOWANCE_GRANTED, ALLOWANCE_EXPIRED, VISIT_BOUGHT
}

data class InterventionEvent(
    val timestamp: Long,
    val itemId: String,
    val displayLabel: String,
    val kind: InterventionKind,
    val boughtMillis: Long = 0L, // only meaningful on VISIT_BOUGHT
)

data class StatsSnapshot(
    val reclaimedMillis: Long,
    val boughtMillis: Long,
    val alarmsByDay: Map<String, Int>, // yyyy-MM-dd → count of ALARM_FIRED
    val worstOffenders: List<Pair<String, Int>>,
    val giveInFreeStreakDays: Int,
)

object StatsAggregator {
    /** Day keys and streak use LocalDate in the device zone; pass pre-bucketed day keys from Android via Clock helper, or accept epochMillis + ZoneId as strings in tests. */
    fun aggregate(events: List<InterventionEvent>, today: java.time.LocalDate): StatsSnapshot
}
```

**Bought-time mechanism (fixes once-per-visit accounting):**
1. On give-in: append `GAVE_IN` (no boughtMillis) **and** `ALLOWANCE_GRANTED`. For STRICT, also conceptually “bought” = 5 min counted from the GAVE_IN row (`boughtMillis==0` means “count as 5 min” for strict only — see aggregator rule below).
2. Persist `visitGrantStartedAt[itemId]` in VisitTracker (or TemporaryAllowanceManager) when `grantVisit` runs.
3. When a visit ends (`VisitTracker.endVisit` / service / UrlInterceptor): if a visit-scoped allowance was active, append **`VISIT_BOUGHT`** with `boughtMillis = now - visitGrantStartedAt` (clamped ≥0), then clear allowance + grant start. Do **not** append a second `GAVE_IN`.
4. Aggregator:
   - Reclaimed: each `ESCAPED` → +5 min
   - Bought: each `GAVE_IN` whose item later has no matching need — **simpler rule:** each `GAVE_IN` where policy was strict OR we don't know → wait: at GAVE_IN time we know mode. Store on GAVE_IN event: use `boughtMillis=0` to mean “strict 5 min”; once-per-visit GAVE_IN contributes **0** bought until `VISIT_BOUGHT` arrives. Each `VISIT_BOUGHT` adds its `boughtMillis`.
   - Streak: only `GAVE_IN` kinds break the streak (`VISIT_BOUGHT` does not)

- [ ] **Step 1: Write StatsAggregatorTest** covering reclaimed, strict bought via GAVE_IN, once-per-visit bought via VISIT_BOUGHT only, streak ignores VISIT_BOUGHT, streak resets on GAVE_IN, empty-day continues streak

- [ ] **Step 2: FAIL → implement aggregator → PASS**

- [ ] **Step 3: Implement InterventionLog + `prune(events, cutoff)` (90 days)**

- [ ] **Step 4: Commit** — `Add intervention log and stats aggregation.`

---

### Task 8: ConfessionNormalizer + ConfessionSentence

**Files:**
- Create: `ConfessionNormalizer.kt`, `ConfessionSentence.kt`
- Create: `ConfessionNormalizerTest.kt`

**Interfaces:**
```kotlin
object ConfessionNormalizer {
    fun normalize(s: String): String // lowercase, collapse whitespace, strip trailing .!? 
    fun matches(spoken: String, expected: String): Boolean
}

object ConfessionSentence {
    const val FALLBACK_CREATIVE = "my creative work"
    fun build(blockedDisplay: String, creativeDisplay: String): String =
        "I choose $blockedDisplay over $creativeDisplay."
    fun pickCreativeDisplay(lastCreativeId: String?, creativeIds: List<String>, resolveName: (String) -> String): String
}
```
- `pickCreativeDisplay`: if `lastCreativeId` in list → its name; else first by name sort; else FALLBACK

- [ ] **Step 1–4: TDD normalizer + pickCreativeDisplay**

- [ ] **Step 5: Commit** — `Add confession sentence builder and normalizer.`

---

### Task 9: Raycast theme shell (Milestone 1 — emulator)

**Files:**
- Modify: `res/values/colors.xml`, `themes.xml`, `CreativitismApp.kt`, `activity_main.xml` (background/text colors), button styles
- Add: Inter TTFs under `res/font/` + `inter.xml` family (bundled; no Play Services download)
- Remove force-light: `AppCompatDelegate.MODE_NIGHT_YES` or dark-only theme
- Delete unused tokens in the same pass or leave stubs mapped: `cv_ink`→alias `cv_text`, `cv_teal`→`cv_escape`, `cv_paper_*`/`cv_glass_*`/`cv_frost_*`/`cv_wash_*`/`cv_disc_*`/`cv_ring_track` → remove after layouts stop referencing (Task 20 finishes orphans). Fix colors.xml header comment (no `values-night`).

**Interfaces:** Color names (keep `cv_` prefix, new meanings):

| Name | Hex |
|---|---|
| `cv_bg` | `#07080a` |
| `cv_surface` | `#101111` |
| `cv_card` | `#1b1c1e` |
| `cv_text` | `#f9f9f9` |
| `cv_text_secondary` | `#9c9c9d` |
| `cv_text_dim` | `#6a6b6c` |
| `cv_blocked` | `#FF6363` |
| `cv_creative` | `#5fc992` |
| `cv_escape` | `#55b3ff` |
| `cv_border` | `#252829` |

- [ ] **Step 1: Replace color tokens; remap layouts to new names**

- [ ] **Step 2: Update theme + main layout so MainActivity is readable dark Raycast**

- [ ] **Step 3: Emulator check** — launch app, verify background `#07080a`, lists readable, protection switch works

- [ ] **Step 4: Commit** — `Restyle main UI to Raycast dark tokens.`

**Delegate candidate:** Composer 2.5 for XML/color/font plumbing; orchestrator reviews + emulator.

---

### Task 10: AlarmActivity UI + AlarmAudioController + service cutover (Milestone 2)

**Files:**
- Create: `AlarmActivity.kt`, `activity_alarm.xml`, `AlarmAudioController.kt`, `res/raw/alarm_klaxon.*`, opaque `Theme.Creativitism.Alarm`
- Modify: `AndroidManifest.xml`, `strings.xml`, **`RedirectorService.kt`** (launch AlarmActivity in this task — must compile)
- Delete in the **same commit** after service compiles: `BlockedInterstitialActivity.kt`, `activity_blocked_interstitial.xml`

**Manifest contract for `AlarmActivity` (copy from interstitial, adapt):**
```xml
<activity
    android:name=".AlarmActivity"
    android:exported="false"
    android:launchMode="singleTask"
    android:taskAffinity="com.creativitism.appredirector.alarm"
    android:excludeFromRecents="true"
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:theme="@style/Theme.Creativitism.Alarm" />
```
- Theme: **opaque** near-black/red (not translucent) — no peeking at blocked content
- Implement `onNewIntent` → `handleIntent` like the old interstitial
- Extra keys: `EXTRA_BLOCKED_ITEM = "BLOCKED_ITEM"`, `EXTRA_DISPLAY_NAME = "DISPLAY_NAME"` — service must use these names in the same commit (old key `BLOCKED_PACKAGE` dies with the interstitial)

**Interfaces:**
```kotlin
class AlarmActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_BLOCKED_ITEM = "BLOCKED_ITEM"
        const val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"
        @Volatile var isShowing: Boolean = false
            private set
    }
}

class AlarmAudioController(context: Context) {
    fun start(countdownMillis: Long)
    fun stop()
    fun isAudible(): Boolean
}
```

**Audio lifecycle:** call `audio.stop()` in `onPause` (and `onDestroy` as safety). Spec: leave without grant → silence. Note: `isShowing` clears in `onPause`, so stop audio **before** or as part of the same pause path so a rapid re-launch does not overlap two players.

**UI requirements:**
- Pulse background red ≤2.5 Hz (`ValueAnimator` period ≥400ms)
- Title `STOP.`; subtitle offense; giant countdown
- Escape always enabled (`cv_escape`)
- Give-in: if confession off → enable after 10s; if confession on → keep disabled / show “Say the trade…” placeholder until Task 15
- Strict grant only in this task for give-in
- Log `ALARM_FIRED` once when handling a new blocked item (not in the service)

**Service change in this task:** replace interstitial launch with:
```kotlin
Intent(this, AlarmActivity::class.java).apply {
    putExtra(AlarmActivity.EXTRA_BLOCKED_ITEM, foregroundApp)
    putExtra(AlarmActivity.EXTRA_DISPLAY_NAME, label)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```
Keep `AlarmActivity.isShowing` guard + 3s debounce. Schedule/visit wiring stays for Task 11.

- [ ] **Step 1: Generate short mono WAV klaxon into `res/raw/alarm_klaxon.wav`** (<100KB)

- [ ] **Step 2: Implement `AlarmAudioController` (USAGE_ALARM, mute → aggressive vibe)**

- [ ] **Step 3: Implement `AlarmActivity` + opaque theme + configChanges/portrait**

- [ ] **Step 4: Rewire `RedirectorService` to AlarmActivity; delete interstitial; confirm project compiles**

- [ ] **Step 5: Emulator** — blocked app → flash/sound/vibe; escape; give-in after 10s; back/home silences without grant; rotate does not restart countdown

- [ ] **Step 6: Commit** — `Replace calm pause with fire-alarm activity.`

---

### Task 11: RedirectorService — schedule, visits, mid-alarm dormancy

**Files:**
- Create: `VisitTracker.kt`
- Modify: `RedirectorService.kt`, `RedirectionManager.kt` / MainActivity remove paths (policy + allowance + visit clear)

**Interfaces:**
```kotlin
class VisitTracker(context: Context) {
    /** All timestamps are System.currentTimeMillis(). */
    fun touch(itemId: String, now: Long = System.currentTimeMillis())
    fun lastSeen(itemId: String): Long
    fun isVisitOpen(itemId: String, now: Long = System.currentTimeMillis()): Boolean
    fun openVisitIds(): Set<String>
    fun endVisit(itemId: String)
    fun markVisitGrantStarted(itemId: String, now: Long = System.currentTimeMillis())
    fun visitGrantStartedAt(itemId: String): Long // 0 if none
    fun clearVisitGrantStarted(itemId: String)
}
```

**`endVisit` side effects (call from service / UrlInterceptor):** clear visit-scoped allowance; if `visitGrantStartedAt != 0`, append `VISIT_BOUGHT` with elapsed millis, then `clearVisitGrantStarted`.

**`checkForegroundApp` logic:**
1. Load schedule with wall-clock local time; update notification armed/dormant  
2. If `AlarmActivity.isShowing`:
   - If schedule **dormant** → send ordered broadcast or set a static/`LocalBroadcast`/`AlarmActivity.requestDismissForDormant()` flag that finishes the alarm **without grant** and stops audio (spec mid-alarm dormancy). Prefer a package-private companion `fun dismissIfShowingForDormant()` invoked from the service on the main thread via `Handler(Looper.getMainLooper())`.
   - Else return (do not relaunch)
3. `foreground = getForegroundApp() ?: return`  
4. If creative package → `settings.lastCreativeItem = foreground`  
5. For each `id in visitTracker.openVisitIds()`: if `id == foreground` (or domain still active — domains handled in Task 12) → `touch`; else if `!isVisitOpen(id, now)` → `endVisit(id)`  
6. If `foreground == packageName` return  
7. If not blocked return  
8. If allowed (strict / visit) return  
9. Debounce 3s → launch `AlarmActivity` (**do not** log ALARM_FIRED here)

Notification text: armed vs `dormant — protection resumes at HH:mm`.

- [ ] **Step 1: Implement VisitTracker with `openVisitIds` + grant-start fields**

- [ ] **Step 2: Rewrite monitoring loop + mid-alarm dormant dismiss**

- [ ] **Step 3: Emulator** — strict re-alarm after 5 min; set schedule outside window while alarm showing → alarm dismisses without grant

- [ ] **Step 4: Commit** — `Wire service visits, schedule gate, and dormant dismiss.`

---

### Task 12: Website alarm path (Milestone 3)

**Files:**
- Modify: `UrlInterceptorService.kt`, `AlarmActivity.kt` (domain give-in return)

**Logic:**
- On blocked URL match: resolve canonical blocked entry from the set
- If dormant / allowed / `AlarmActivity.isShowing` → return
- Else launch `AlarmActivity` with domain as `EXTRA_BLOCKED_ITEM`
- `visitTracker.touch(domain)` when matching URL seen
- **Navigate-away:** when URL is non-null, differs from last, and no longer matches that domain → do **not** immediately end visit; stop touching that domain so absence clock runs. Optionally keep `lastMatchedDomain`; when a different non-matching URL is seen continuously, still rely on 5 min absence via VisitTracker (touch only while matched). When Chrome leaves foreground, Task 11’s openVisitIds loop ends domain visits the same as apps.
- **Also:** if URL changes from domain A to unrelated site, stop touching A (absence begins). If URL changes to domain B (also blocked), touch B.

**Domain give-in return (AlarmActivity):**
```kotlin
if (itemLooksLikeDomain(blockedItem)) {
    // Prefer finish() so Chrome underneath resurfaces; if needed:
    packageManager.getLaunchIntentForPackage("com.android.chrome")?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(it)
    }
} else {
    packageManager.getLaunchIntentForPackage(blockedItem)?.let { startActivity(it.addFlags(...)) }
}
```

- [ ] **Step 1: Implement launch + allowance/schedule + touch/navigate-away**

- [ ] **Step 2: Domain give-in brings Chrome forward**

- [ ] **Step 3: Emulator** — block a domain, alarm shows; give-in returns to Chrome; navigate away 5+ min ends visit

- [ ] **Step 4: Commit** — `Route blocked websites through the fire alarm.`

---

### Task 13: Blocked list row — enforcement + confession toggles (Milestone 4 UI)

**Files:**
- Create: `item_blocked_row.xml` (locked choice — do not modify creative `item_managed_item.xml` for these controls)
- Modify: `ManagedItemsAdapter.kt`, `MainActivity.kt`
- On remove blocked item: `BlockedItemPolicyStore.remove` + `allowanceManager.clear` + `visitTracker.endVisit` / remove lastSeen

**UI:** Strict/Once control + confession Switch; persist via `BlockedItemPolicyStore.set`.

- [ ] **Step 1: Adapter binds policy; callbacks `onPolicyChanged(id, policy)`**

- [ ] **Step 2: Emulator** — toggle modes, restart app, values persist

- [ ] **Step 3: Commit** — `Add per-item enforcement and confession controls.`

**Delegate candidate:** Composer 2.5 for row XML.

---

### Task 14: Full give-in paths honor enforcement mode

**Files:**
- Modify: `AlarmActivity.kt` give-in label + grant + logging:
  - STRICT → `grantStrict`; label “Use it for 5 minutes”; log `GAVE_IN` + `ALLOWANCE_GRANTED` (bought counted as 5 min in aggregator)
  - ONCE_PER_VISIT → `grantVisit` + `visitTracker.touch` + `markVisitGrantStarted`; label “Use it this visit”; log `GAVE_IN` + `ALLOWANCE_GRANTED` (bought deferred to `VISIT_BOUGHT` on endVisit)
  - Domain items: Chrome return path from Task 12

- [ ] **Step 1: Implement**

- [ ] **Step 2: Emulator** — once-per-visit no re-alarm until 5 min away; strict re-alarms; domain give-in → Chrome

- [ ] **Step 3: Commit** — `Honor strict vs once-per-visit allowances in alarm.`

---

### Task 15: Confession flow (Milestone 5)

**Files:**
- Create: `ConfessionSpeechHelper.kt` (**required** — owns SpeechRecognizer create/destroy on main thread)
- Modify: `AlarmActivity.kt`, `AndroidManifest.xml` (`RECORD_AUDIO`), `strings.xml` (mic disclosure)

**API gating:**
- Prefer on-device recognition only when `Build.VERSION.SDK_INT >= 31` **and** `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`
- Else use default `SpeechRecognizer.createSpeechRecognizer` (may use network speech service — still do not store audio; disclose in privacy that the platform speech service may process audio transiently when on-device is unavailable)
- If mic denied, recognizer missing, or error → typed fallback only

**Emulator gate:** verify **typed** confession end-to-end. Do not require speak-path on emulator (often no speech services). Manual device check for speak optional.

**Flow after countdown if confession enabled:**
1. Build sentence via `ConfessionSentence` (stable creative pick for the session)
2. Show sentence + mic control + typed field
3. Disclosure dialog before first `RECORD_AUDIO` request
4. On normalize-match → enable give-in; user must still tap give-in
5. On mismatch → retry, do not unlock

- [ ] **Step 1: Typed path works without mic**

- [ ] **Step 2: Speech helper with API gating + permission disclosure**

- [ ] **Step 3: Emulator** — confession on/off; deny mic → type works

- [ ] **Step 4: Commit** — `Require spoken or typed confession before give-in.`

---

### Task 16: Escape target — resume last work

**Files:**
- Modify: `AlarmActivity.redirectSomewhereBetter`, `AppSettingsStore`, `RedirectionManager`

```kotlin
fun escapeTarget(settings: AppSettingsStore, redirection: RedirectionManager): String? {
    if (settings.resumeLastWork) {
        val last = settings.lastCreativeItem
        if (last != null && redirection.getCreativityBoostingItems().contains(last)) return last
    }
    return redirection.getRandomCreativityBoostingItem()
}
```
- On escape: stop audio, log ESCAPED, update `lastCreativeItem` when launching creative, finish

- [ ] **Step 1: Implement + MainActivity toggle “Resume my last work”**

- [ ] **Step 2: Emulator**

- [ ] **Step 3: Commit** — `Add resume-last-work escape setting.`

---

### Task 17: Active hours UI (Milestone 6)

**Files:**
- Modify: `activity_main.xml`, `MainActivity.kt`, `ScheduleStore`

- Time pickers for start/end; optional weekday chips; enable switch
- Status line bound from `SchedulePolicy.isArmed` + resume label
- Service already gates (Task 11); verify notification updates when crossing boundary (optional AlarmManager nudge at edges — **YAGNI:** poll every 500ms is enough to flip notification)

- [ ] **Step 1: UI + persist**

- [ ] **Step 2: Emulator** — set window to exclude now → dormant, apps free; inside window → alarm

- [ ] **Step 3: Commit** — `Add active-hours schedule controls.`

---

### Task 18: Stats section + weekly receipt (Milestone 7)

**Files:**
- Modify: `activity_main.xml`, `MainActivity.kt`, `AndroidManifest.xml` (FileProvider)
- Create: `WeeklyReceiptHelper.kt`, `res/xml/file_paths.xml`
- Confirm logging already wired: AlarmActivity (`ALARM_FIRED`, `ESCAPED`, `GAVE_IN`, `ALLOWANCE_GRANTED`); VisitTracker/service (`VISIT_BOUGHT`, optional `ALLOWANCE_EXPIRED` on strict expiry observation)

**Receipt:**
- Week id via `java.time.LocalDate` + ISO week in device zone
- If `lastReceiptWeekId != previousWeekId` and prior week has events → show card
- Copy: `This week you traded {reclaimed} of scrolling for time in your Creative apps.`
- Share: render to bitmap → write under `cache/receipts/` → `FileProvider.getUriForFile` → `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`. Never `Uri.fromFile`.

- [ ] **Step 1: Show stats from `StatsAggregator.aggregate(log.all(), LocalDate.now())`**

- [ ] **Step 2: FileProvider + receipt prompt + share**

- [ ] **Step 3: Emulator** — escape/give-in update stats; share sheet opens without FileUriExposedException

- [ ] **Step 4: Commit** — `Add local stats and weekly trade receipt.`

**Delegate candidate:** Composer 2.5 for stats/receipt layout.

---

### Task 19: Icons + store docs + backup exclusions (Milestone 8)

**Files:**
- Modify: launcher/notification drawables (`ic_launcher_*`, `ic_tilde`),  
  `docs/playstore/PRIVACY_POLICY.md`, `STORE_LISTING.md`, `DECLARATIONS.md`, `docs/privacy/index.html`,
  `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

**Backup exclusions (both XML files):** exclude sharedpref paths:
`redirections_v2.xml`, `temporary_allowances.xml`, `blocked_item_policy.xml`, `active_hours.xml`, `app_settings.xml`, `intervention_log.xml`, `visit_tracker.xml` (use actual prefs filenames). Remove stale `redirections.xml` exclusion or keep it harmlessly alongside.

**Privacy must state:** fire alarm sound/vibration; optional mic / platform speech; never stored by Creativitism; local stats not cloud-synced (Auto Backup exclusions); share only on explicit action.

- [ ] **Step 1: Restyle icons (tilde + cool accent on `#07080a`)**

- [ ] **Step 2: Update store/privacy docs**

- [ ] **Step 3: Fix backup + data extraction rules**

- [ ] **Step 4: Emulator** — launcher icon; smoke main + alarm

- [ ] **Step 5: Commit** — `Update icons, disclosures, and backup exclusions.`

**Delegate candidate:** Composer 2.5 for docs + icon XML.

---

### Task 20: Final integration pass

- [ ] **Step 1: Run full unit tests** — `./gradlew :app:testDebugUnitTest` all green

- [ ] **Step 2: Emulator checklist** against spec fixed values (10s, strict default, confession default, website alarm, schedule, mute→vibrate, receipt share, mid-alarm dormant dismiss)

- [ ] **Step 3: Remove dead paper/glass/frost drawables, orphaned `cv_*` color tokens, leftover interstitial strings/themes**

- [ ] **Step 4: Commit** — `Clean dead light-theme resources after fire-alarm backport.`

---

## Emulator milestone gates (orchestrator)

| After task | Gate |
|---|---|
| 9 | Main screen Raycast dark |
| 10 | Alarm on blocked app (audio stop on leave; rotation stable) |
| 11 | Visits + schedule + mid-alarm dormant dismiss |
| 12 | Website → alarm + Chrome return |
| 13–14 | Enforcement modes + logging |
| 15 | Confession (typed on emulator) |
| 16–17 | Resume + schedule UI |
| 18 | Stats/receipt via FileProvider |
| 19–20 | Icons/docs/backup + cleanup |

---

## Plan self-review

| Spec area | Task(s) |
|---|---|
| Fire alarm UI/audio/mute fallback | 10 |
| Alarm manifest / audio lifecycle / configChanges | 10 |
| Enforcement modes + visits | 1–4, 11, 13–14 |
| Visit bought-time close-out | 7, 11, 14 |
| Confession | 8, 15 |
| Stats + receipt + FileProvider | 7, 18 |
| Active hours + mid-alarm dormancy | 5–6, 11, 17 |
| Websites join alarm + Chrome return | 12 |
| Raycast visual + icons | 9, 19 |
| Store docs + backup exclusions | 19 |
| Escape / resume last work | 16 |
| Defaults & fixed timings | 1, 4, 10 |
| Clock consistency / VISIT_SCOPED cleanup | Global, 4, 11 |
| Privacy / no network / no cloud backup | Global + 19 |

Incorporates `cursor-plan-review-2026-07-29-125542.md` suggested fixes 1–8.
