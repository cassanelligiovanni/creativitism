# Fire-Alarm Creativitism Backport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Creativitism’s calm pause with a fire-alarm intervention, add per-item enforcement/confession, active hours, local stats/receipt, and Raycast dark UI — keeping lists, permissions, and on-device privacy.

**Architecture:** Evolve in place on Views/XML. Pure JVM policy objects (`*Policy`, `StatsAggregator`, `ConfessionNormalizer`) own decisions and are unit-tested; thin SharedPreferences stores wrap them; `RedirectorService` / `UrlInterceptorService` launch `AlarmActivity` instead of the old interstitial / instant redirect.

**Tech Stack:** Kotlin, Android Views + ViewBinding, Material 3, SharedPreferences, UsageStats FGS, AccessibilityService (Chrome), SpeechRecognizer, JUnit4 JVM tests, Gradle AGP 8.7 / compileSdk 35 / minSdk 26.

**Spec:** `docs/superpowers/specs/2026-07-29-fire-alarm-backport-design.md`

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
| `app/src/main/java/.../WeeklyReceiptHelper.kt` | Receipt copy + share bitmap |
| `app/src/main/res/layout/activity_alarm.xml` | Alarm layout |
| `app/src/main/res/layout/item_blocked_row.xml` | Blocked row with mode + confession |
| `app/src/main/res/layout/section_stats.xml` (or inline in main) | Stats block |
| `app/src/main/res/raw/alarm_klaxon.wav` | Short loopable klaxon |
| `app/src/main/res/font/inter.xml` (+ ttf or downloadable font) | Inter family |
| `app/src/test/java/.../*PolicyTest.kt`, `StatsAggregatorTest.kt`, `ConfessionNormalizerTest.kt`, `VisitPolicyTest.kt`, `SchedulePolicyTest.kt` | JVM tests |

### Modify

| File | Change |
|---|---|
| `AllowancePolicy.kt` | Add visit-scoped sentinel helpers |
| `TemporaryAllowanceManager.kt` | Strict + visit grants; clear visit allowance |
| `RedirectionManager.kt` | Escape target helper; cleanup policy on remove |
| `RedirectorService.kt` | Schedule, visits, allowances, launch AlarmActivity, notification armed/dormant |
| `UrlInterceptorService.kt` | Launch AlarmActivity; respect schedule/allowance |
| `MainActivity.kt` | Theme, schedule UI, stats, resume setting, receipt |
| `ManagedItemsAdapter.kt` | Blocked row controls |
| `CreativitismApp.kt` | Force dark / night yes |
| `AndroidManifest.xml` | AlarmActivity, RECORD_AUDIO, VIBRATE |
| `colors.xml`, `themes.xml`, `strings.xml`, layouts, icons | Raycast restyle |
| Store docs under `docs/playstore/`, `docs/privacy/` | Alarm, mic, stats |

### Delete / retire after AlarmActivity ships

- `BlockedInterstitialActivity.kt`, `activity_blocked_interstitial.xml` (or keep as deprecated stub that forwards — prefer delete + manifest swap)
- Paper/glass drawables unused after restyle

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
  - `fun isVisitScoped(expiryMillis: Long): Boolean`
  - `fun isAllowed(expiryMillis: Long, now: Long, visitStillOpen: Boolean): Boolean`  
    — if visit-scoped → `visitStillOpen`; else existing timed check
- `TemporaryAllowanceManager`:
  - `grantStrict(itemId, now)`
  - `grantVisit(itemId)` → store `VISIT_SCOPED`
  - `isAllowed(itemId, now, visitStillOpen: Boolean)`
  - `clear(itemId)`

- [ ] **Step 1: Extend unit tests for visit-scoped allowed/denied**

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement policy + manager methods (keep old `grant` as alias to `grantStrict` for compatibility until call sites update)**

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
enum class InterventionKind { ALARM_FIRED, ESCAPED, GAVE_IN, ALLOWANCE_GRANTED, ALLOWANCE_EXPIRED }

data class InterventionEvent(
    val timestamp: Long,
    val itemId: String,
    val displayLabel: String,
    val kind: InterventionKind,
    val boughtMillis: Long = 0L, // for once-per-visit close-out
)

data class StatsSnapshot(
    val reclaimedMillis: Long,
    val boughtMillis: Long,
    val alarmsByDay: Map<String, Int>, // yyyy-MM-dd → count of ALARM_FIRED
    val worstOffenders: List<Pair<String, Int>>, // label → alarm count, desc
    val giveInFreeStreakDays: Int,
)

object StatsAggregator {
    fun aggregate(events: List<InterventionEvent>, todayEpochDay: Long, zoneOffsetMs: Int = 0): StatsSnapshot
}
```
- Reclaimed: each ESCAPED → +5 min  
- Bought: each GAVE_IN with `boughtMillis==0` → +5 min; else add `boughtMillis`  
- Streak: walk back calendar days from today; increment while day has 0 GAVE_IN; stop at first day with ≥1 GAVE_IN  
- `InterventionLog`: JSON array in prefs `"intervention_log"`, prune events older than 90 days on append; `fun append`, `fun all(): List<InterventionEvent>`

- [ ] **Step 1: Write StatsAggregatorTest** covering reclaimed, bought, streak reset on give-in, empty-day continues streak

- [ ] **Step 2: FAIL → implement aggregator → PASS**

- [ ] **Step 3: Implement InterventionLog (use org.json.JSONArray already on Android; for JVM unit tests of prune logic, extract `fun prune(events, cutoff): List`)**

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
- Add: Inter font (`res/font/inter.xml` + font files or downloadable-fonts XML pointing at Google Fonts provider)
- Remove force-light: set `AppCompatDelegate.MODE_NIGHT_YES` or use a dark-only theme without DayNight flip

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

- [ ] **Step 1: Replace color tokens; map old references** (`cv_ink`→`cv_text`, `cv_teal`→`cv_escape` or `cv_creative` by role, paper→`cv_bg`)

- [ ] **Step 2: Update theme + main layout so MainActivity is readable dark Raycast**

- [ ] **Step 3: Emulator check** — launch app, verify background `#07080a`, lists readable, protection switch works

- [ ] **Step 4: Commit** — `Restyle main UI to Raycast dark tokens.`

**Delegate candidate:** Composer 2.5 for XML/color/font plumbing; orchestrator reviews + emulator.

---

### Task 10: AlarmActivity UI + AlarmAudioController (Milestone 2)

**Files:**
- Create: `AlarmActivity.kt`, `activity_alarm.xml`, `AlarmAudioController.kt`, `res/raw/alarm_klaxon.*`
- Modify: `AndroidManifest.xml` (replace interstitial activity registration), `strings.xml`
- Delete or stop using: `BlockedInterstitialActivity`

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
    fun start(countdownMillis: Long) // loop klaxon USAGE_ALARM, escalate volume, vibrate; if !audible → aggressive vibe
    fun stop()
    fun isAudible(): Boolean // alarm stream volume > 0
}
```

**UI requirements:**
- Opaque full-screen; pulse background red ≤2.5 Hz (`ValueAnimator` period ≥400ms)
- Title `STOP.`; subtitle offense; giant countdown
- Escape button always enabled (blue `#55b3ff`)
- Give-in disabled until 10s (confession wiring in Task 13 — for now unlock like old interstitial if confession off; if confession on, still unlock button visually but Task 13 gates it — **for this task: honor confession=false path only; if confession true, keep button disabled after countdown until Task 13**, OR temporarily treat as off until Task 13. Prefer: read policy; if confession on, show placeholder “Say the trade…” disabled state without mic yet)

**Wire temporarily:** `RedirectorService` still references interstitial — update in Task 11. For Milestone 2, change service to launch `AlarmActivity` with same extra key mapping (`EXTRA_BLOCKED_ITEM`).

- [ ] **Step 1: Generate short mono WAV klaxon into `res/raw/alarm_klaxon.wav`** (script or checked-in binary; keep <100KB)

- [ ] **Step 2: Implement `AlarmAudioController` with MediaPlayer loop + Vibrator; mute fallback**

- [ ] **Step 3: Implement `AlarmActivity` layout + countdown + escape/give-in (strict grant only for now)**

- [ ] **Step 4: Manifest + remove old interstitial**

- [ ] **Step 5: Emulator** — block a test app, confirm flash/sound/vibe, escape works, give-in after 10s returns, back goes home without grant

- [ ] **Step 6: Commit** — `Replace calm pause with fire-alarm activity.`

---

### Task 11: RedirectorService integration — schedule, visits, policies

**Files:**
- Create: `VisitTracker.kt`
- Modify: `RedirectorService.kt`, `RedirectionManager.kt` (on remove blocked → `policyStore.remove`)

**Interfaces:**
```kotlin
class VisitTracker(context: Context) {
    fun touch(itemId: String, now: Long)
    fun lastSeen(itemId: String): Long
    fun isVisitOpen(itemId: String, now: Long): Boolean
    fun endVisit(itemId: String)
}
```

**`checkForegroundApp` logic (exact):**
1. If `AlarmActivity.isShowing` return  
2. Load schedule; if `!SchedulePolicy.isArmed(...)` → update notification dormant; return  
3. `foreground = getForegroundApp() ?: return`  
4. If creative package → `settings.setLastCreativeItem(foreground)`  
5. Update visit tracker: if current blocked item id equals foreground → `touch`; for other open visits with absence → `endVisit` + clear visit allowance  
6. If `foreground == packageName` return  
7. If `!redirectionManager.isSoulSucking(foreground)` return  
8. If `allowanceManager.isAllowed(foreground, now, visitTracker.isVisitOpen(foreground, now))` return  
9. Debounce 3s → start `AlarmActivity` with item + label; log `ALARM_FIRED`

Notification text: armed vs `dormant — protection resumes at HH:mm` using `SchedulePolicy.nextResumeLabel`.

- [ ] **Step 1: Implement VisitTracker**

- [ ] **Step 2: Rewrite `checkForegroundApp` + notification**

- [ ] **Step 3: Emulator** — strict re-alarm after 5 min; notification string

- [ ] **Step 4: Commit** — `Wire service to alarm, visits, and schedule gate.`

---

### Task 12: Website alarm path (Milestone 3)

**Files:**
- Modify: `UrlInterceptorService.kt`

**Logic:**
- On blocked URL match: resolve canonical blocked entry string from set (the matching entry)
- If dormant schedule or allowed (visit/strict) or `AlarmActivity.isShowing` → return
- Else launch `AlarmActivity` with domain item id + display name = domain
- Touch visit for domain when matching URL seen; `RedirectorService` also ends domain visits when Chrome not foreground (track `lastDomainItem` via VisitTracker keys)

- [ ] **Step 1: Implement launch + allowance/schedule checks**

- [ ] **Step 2: Emulator** — block `example.com`, open in Chrome, alarm shows; escape/give-in

- [ ] **Step 3: Commit** — `Route blocked websites through the fire alarm.`

---

### Task 13: Blocked list row — enforcement + confession toggles (Milestone 4 UI)

**Files:**
- Modify: `item_managed_item.xml` or new `item_blocked_row.xml`, `ManagedItemsAdapter.kt`, `MainActivity.kt`
- On remove blocked item: `BlockedItemPolicyStore.remove` + clear allowances

**UI:** For blocked rows only — segmented control or spinner Strict/Once; Switch confession. Persist via `BlockedItemPolicyStore.set`.

- [ ] **Step 1: Adapter binds policy; callbacks `onPolicyChanged(id, policy)`**

- [ ] **Step 2: Emulator** — toggle modes, restart app, values persist

- [ ] **Step 3: Commit** — `Add per-item enforcement and confession controls.`

**Delegate candidate:** Composer 2.5 for row XML.

---

### Task 14: Full give-in paths honor enforcement mode

**Files:**
- Modify: `AlarmActivity.kt` give-in label + grant:
  - STRICT → `grantStrict`; label “Use it for 5 minutes”
  - ONCE_PER_VISIT → `grantVisit`; label “Use it this visit”; `visitTracker.touch`

- [ ] **Step 1: Implement**

- [ ] **Step 2: Emulator** — once-per-visit no re-alarm until 5 min away; strict re-alarms

- [ ] **Step 3: Commit** — `Honor strict vs once-per-visit allowances in alarm.`

---

### Task 15: Confession flow (Milestone 5)

**Files:**
- Modify: `AlarmActivity.kt`, `AndroidManifest.xml` (`RECORD_AUDIO`), `strings.xml` (mic disclosure)
- Optional: `ConfessionSpeechHelper.kt` wrapping SpeechRecognizer

**Flow after countdown if confession enabled:**
1. Build sentence via `ConfessionSentence`
2. Show sentence + mic button / listening state; typed field fallback
3. MainActivity-style disclosure dialog before first `RECORD_AUDIO` request
4. On match → enable give-in button (or auto-enable and wait for tap — **require explicit tap on give-in after unlock**)
5. On mismatch → retry, do not unlock

- [ ] **Step 1: Typed path works without mic**

- [ ] **Step 2: Speech path + permission disclosure**

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
- Modify: `activity_main.xml`, `MainActivity.kt`
- Create: `WeeklyReceiptHelper.kt`
- Wire logging in AlarmActivity + service (ALARM_FIRED, ESCAPED, GAVE_IN, ALLOWANCE_EXPIRED)

**Receipt:**
- Week id Mon–Sun; if `lastReceiptWeekId != previousWeekId` and prior week has events → show dialog/card with copy:  
  `This week you traded {reclaimed} of scrolling for time in your Creative apps.`
- Share: render layout to bitmap → `Intent.ACTION_SEND` image/*  
- Only on user tap Share

- [ ] **Step 1: Show stats from `StatsAggregator.aggregate(log.all(), …)`**

- [ ] **Step 2: Receipt prompt + share**

- [ ] **Step 3: Emulator** — trigger escape/give-in, stats update; share sheet opens

- [ ] **Step 4: Commit** — `Add local stats and weekly trade receipt.`

**Delegate candidate:** Composer 2.5 for stats/receipt layout.

---

### Task 19: Icons + store docs (Milestone 8)

**Files:**
- Modify: launcher/notification drawables (`ic_launcher_*`, `ic_tilde`),  
  `docs/playstore/PRIVACY_POLICY.md`, `STORE_LISTING.md`, `DECLARATIONS.md`, `docs/privacy/index.html`

**Privacy must state:** fire alarm sound/vibration; optional mic for on-device confession never stored/sent; local stats only; share explicit.

- [ ] **Step 1: Restyle icons (tilde + cool accent on `#07080a`)**

- [ ] **Step 2: Update all four store/privacy docs**

- [ ] **Step 3: Emulator** — launcher icon; smoke main + alarm

- [ ] **Step 4: Commit** — `Update icons and store disclosures for the alarm.`

**Delegate candidate:** Composer 2.5 for docs + icon XML.

---

### Task 20: Final integration pass

- [ ] **Step 1: Run full unit tests** — `./gradlew :app:testDebugUnitTest` all green

- [ ] **Step 2: Emulator checklist** against spec fixed values (10s, strict default, confession default, website alarm, schedule, mute→vibrate, receipt)

- [ ] **Step 3: Remove dead paper/glass resources and any leftover interstitial strings**

- [ ] **Step 4: Commit** — `Clean dead light-theme resources after fire-alarm backport.`

---

## Emulator milestone gates (orchestrator)

| After task | Gate |
|---|---|
| 9 | Main screen Raycast dark |
| 10–11 | Alarm on blocked app |
| 12 | Website → alarm |
| 13–14 | Enforcement modes |
| 15 | Confession |
| 16–17 | Resume + schedule |
| 18 | Stats/receipt |
| 19–20 | Icons/docs + cleanup |

---

## Plan self-review

| Spec area | Task(s) |
|---|---|
| Fire alarm UI/audio/mute fallback | 10 |
| Enforcement modes + visits | 1–4, 11, 13–14 |
| Confession | 8, 15 |
| Stats + receipt | 7, 18 |
| Active hours | 5–6, 11, 17 |
| Websites join alarm | 12 |
| Raycast visual + icons | 9, 19 |
| Store docs | 19 |
| Escape / resume last work | 16 |
| Defaults & fixed timings | 1, 4, 10 |
| Privacy / no network | Global + 19 |

No TBD placeholders. Types aligned across tasks (`VISIT_SCOPED`, `ActiveHours`, `InterventionKind`, `AlarmActivity.EXTRA_BLOCKED_ITEM`).
