# Creativitism Fire-Alarm Backport — Design

**Date:** 2026-07-29  
**Status:** Approved for implementation planning  
**Approach:** Evolve in place (Views/XML, existing service/activity contract)

## Goal

Replace the calm “Take a breath.” pause with an aversive full-screen fire alarm, add per-item enforcement and confession, active-hours scheduling, local intervention stats with a weekly receipt, and restyle the whole app to the Raycast-inspired dark design system — while keeping Creativitism’s name, tagline, tilde mark, two lists, permission flows, and on-device privacy stance.

## Non-goals

- Compose migration (stay on Views/XML + ViewBinding + Material)
- Accounts, cloud sync, analytics SDKs, or network calls
- Changing domain matching rules
- Supporting browsers other than Chrome for website interception (unchanged)
- Shipping Estremi Rimedi branding (app stays Creativitism~)

## Fixed behavioral values

| Value | Spec |
|---|---|
| Countdown before give-in unlocks | 10 seconds |
| Strict allowance | 5 minutes; re-alarm immediately on expiry while still in use |
| Once-per-visit allowance | Rest of visit; visit ends after 5 continuous minutes out of foreground, or when closed |
| Defaults | Enforcement = Strict; Confession = On |
| Escape target | Uniform random Creative item each time; optional “resume my last work” uses most recently used Creative item |
| Empty Creative list | Escape silences alarm and leaves the blocked item (home) |
| Active hours | Always-on by default; when set, enforce only inside window |
| Flash rate | Below 3 Hz (target ≤ 2.5 flashes/second) |
| Mute / silent | Play on alarm stream (survives media mute); if inaudible → aggressive vibration for the whole countdown |
| Debounce | Keep existing ~3s per-item interstitial debounce; never stack/re-trigger while alarm is showing |

---

## Architecture

### What stays

- `RedirectorService` — FGS UsageStats poll (~500ms); launches alarm activity; `isShowing` guard; debounce map
- `UrlInterceptorService` — Chrome URL bar via accessibility
- Full-screen activity (not a `SYSTEM_ALERT_WINDOW` overlay) as the block UI
- SharedPreferences for all persistence
- MainActivity as the single settings/lists surface
- Permission walkthroughs (usage stats, overlay, notifications, battery, accessibility)

### New / extended components

| Component | Role |
|---|---|
| `BlockedItemPolicyStore` | Per blocked item: `EnforcementMode` (STRICT / ONCE_PER_VISIT), `confessionEnabled` |
| `VisitTracker` | Per-item last foreground timestamp; visit open/closed; 5-minute absence ends visit |
| `TemporaryAllowanceManager` (extended) | Strict timed expiry **and** visit-scoped allowances; keyed by item id (package or domain) |
| `ScheduleStore` | Optional active-hours window + optional per-weekday mask; null schedule = always armed |
| `InterventionLog` | Append-only local log of alarm/escape/give-in/allowance events |
| `StatsAggregator` | Pure JVM rollups: reclaimed vs bought time, alarms/day, worst offenders, give-in-free streak |
| `AlarmActivity` | Renamed/replaced `BlockedInterstitialActivity` — flash, sound, vibe, confession |
| `AlarmAudioController` | Klaxon loop, volume escalation, vibration pattern; stop on escape/finish |
| `ConfessionHelper` | On-device speech recognition + typed fallback; builds confession sentence from blocked + Creative list |
| `WeeklyReceipt` | Receipt copy + share-as-bitmap on explicit user action |
| Raycast theme tokens | `colors.xml` / `themes.xml` / typography; Inter font; icons |

### Data model

**Lists (existing `redirections_v2`):** unchanged string sets for blocked and creative items.

**Per-item policy** (new prefs file, e.g. `blocked_item_policy`):

```
itemId → { enforcementMode: STRICT|ONCE_PER_VISIT, confession: Boolean }
```

Missing entry ⇒ defaults (STRICT, confession on). Removing a blocked item deletes its policy row.

**Allowances** (extend `temporary_allowances`):

- Strict: `itemId → expiryEpochMillis` (now + 5 min), same cleanup as today
- Once-per-visit: boolean (or sentinel expiry) meaning “allowed until visit ends”; cleared when `VisitTracker` ends the visit

**Visits:**

- App items: UsageStats foreground package == item → refresh `lastSeenAt`. When foreground ≠ item for > 5 continuous minutes → end visit and clear visit-scoped allowance.
- Domain items: while Chrome is foreground and the URL bar matches the blocked domain → refresh `lastSeenAt`. When Chrome leaves foreground, or the URL no longer matches, for > 5 continuous minutes → end visit.
- Brief switches under 5 minutes do not end the visit.

**Schedule:**

```
enabled: Boolean          // false ⇒ always armed (default)
startMinuteOfDay / endMinuteOfDay
weekdaysBitmask           // 0 or unset ⇒ all days
```

Outside window: service stays running but does not launch alarm; notification says dormant and when it resumes. Crossing into the window mid-session re-arms on the next poll tick.

**Settings:**

- `resume_last_work: Boolean` (default false)
- `last_creative_item: String?` — updated whenever escape launches a Creative item; also updated when UsageStats shows a Creative package in the foreground (best-effort)

**Intervention events** (SharedPreferences-backed capped list, prune on write):

```
timestamp, itemId, displayLabel, kind: ALARM_FIRED | ESCAPED | GAVE_IN | ALLOWANCE_GRANTED | ALLOWANCE_EXPIRED
```

Retention: rolling **90 days**. No network. Log `ALARM_FIRED` when the alarm activity starts; `ESCAPED` / `GAVE_IN` on those exits; `ALLOWANCE_GRANTED` with give-in; `ALLOWANCE_EXPIRED` when the service observes a strict expiry (optional, for stats fidelity).

### Protection decision path (apps)

Every poll tick:

1. If `AlarmActivity.isShowing` → return  
2. If schedule dormant → return (update notification if state changed)  
3. Resolve foreground package  
4. If not blocked → update visit tracker for any previous blocked item (absence); return  
5. If allowed (strict not expired / once-per-visit visit still open) → return  
6. Debounce → launch `AlarmActivity` with item id + display name  

On strict allowance expiry while still foreground: next tick fails step 5 → alarm fires again at full intensity (no grace).

### Website path

On Chrome URL change matching a blocked domain:

1. Resolve matched blocked entry (domain string)  
2. If schedule dormant or domain currently allowed → no-op  
3. If alarm already showing → no-op  
4. Else launch `AlarmActivity` with domain as item id (same UI/economy as apps)  
5. Do **not** call `RedirectProxyActivity` directly for blocked URLs anymore  

Escape/give-in for domains: escape opens Creative item; give-in grants domain allowance and returns user to Chrome (bring Chrome to front; do not navigate away from the URL unless already redirected). Prefer finishing the alarm so Chrome remains underneath when possible; if needed, relaunch Chrome.

### Alarm activity behavior

**Entry:** `EXTRA_BLOCKED_ITEM` (package or domain), optional `EXTRA_DISPLAY_NAME`.

**Visual / audio:**

- Full-screen opaque (no peeking at blocked content through frost)
- Background pulses deep red ↔ bright red, synced to alarm rhythm, ≤ 2.5 Hz
- Typography: huge **STOP.**; “You were opening {name}.”; giant countdown 10→0 with sub-second progress
- `AlarmAudioController`: `AudioAttributes.USAGE_ALARM` stream (separate from media — still audible when the user has only muted music/video), looping klaxon from `res/raw/alarm_klaxon` (short generated tone loop checked into the repo; ToneGenerator fallback only if MediaPlayer fails), volume escalates over 10s on the **alarm** stream; vibration pattern runs with it
- **Mute / silent handling:** Prefer sound via the alarm stream so a muted media volume does not silence the intervention. Before/during playback, if alarm stream volume is 0, playback fails, or the device is in a state where alarm audio will not be heard, run an **aggressive vibration pattern for the whole countdown** (and keep vibrating on give-in wait) so the intervention is never silent-and-still. Do not request DND-bypass or notification-policy privileges solely to force speaker output — stay within normal app capabilities; document that total silence + vibration-only is possible if the user has zeroed alarm volume / blocked alarms in system settings
- No mute control inside the alarm UI

**Exit paths:**

1. **Take me somewhere better** (always enabled; calm blue/green accent)  
   - Stop audio/vibe immediately  
   - Log ESCAPED  
   - Destination: random Creative, or last Creative if `resume_last_work`  
   - Empty Creative → `ACTION_MAIN`/`CATEGORY_HOME`  
   - `finish()`  

2. **Give-in** (label: “Use it for 5 minutes” or “Use it this visit”)  
   - Disabled until countdown completes  
   - If confession on: after countdown, show confession UI; button enables only after successful speak/type match  
   - If confession off: enable button when countdown ends  
   - On press: grant allowance, log GAVE_IN, return to blocked item, `finish()`, stop audio  

**Leave without either:** Back / home / task switch away → stop audio, do not grant allowance, do not open blocked item; `isShowing` clears so service can re-fire if they return to blocked item (subject to debounce).

**Confession:**

- Sentence template: `I choose {blocked} over {creative}.`
- `{creative}` is chosen **once when the confession UI appears** and held stable for that alarm session: prefer `last_creative_item` if it is still on the Creative list; else the first Creative item by display-name sort; else the fixed fallback **“my creative work”**.
- Prefer `SpeechRecognizer` with on-device preference where the platform exposes it; never record, store, or upload audio.
- Normalize comparison: case-insensitive, collapse whitespace, strip trailing punctuation.
- Mic: explain-then-request on first need; refuse or unavailable → typed `EditText` that must match under the same normalization.
- Manifest: add `RECORD_AUDIO`; disclose in privacy policy.

### Main screen

- Raycast dark theme throughout (see Visual system)
- Blocked list rows: name/icon + inline enforcement control (Strict / Once per visit) + confession toggle
- Creative list: unchanged structure; cool accent
- Protection: master switch (existing flow) + active-hours editor + status line (“armed” / “dormant — protection resumes at HH:MM”)
- Optional setting: “Resume my last work”
- Stats section: time reclaimed, time bought, alarms per day, worst offenders by alarm count, give-in-free streak
- Weekly receipt: show when MainActivity opens if the local week (Mon–Sun) differs from the last-shown receipt week and the log has events in the prior week; dismissible; share image only on explicit tap

**Time accounting:**

- **Time reclaimed:** each `ESCAPED` event adds 5 minutes.
- **Time bought:** each `GAVE_IN` on a strict item adds 5 minutes; each once-per-visit `GAVE_IN` adds elapsed time from grant until visit end (recorded when the visit ends).
- **Give-in-free streak:** walking backward from today, count consecutive local calendar days with zero `GAVE_IN` events; stop at the first day that has a give-in. Days with no log events count as give-in-free.

### Visual system

Authority: Raycast-inspired design system from product brief.

| Role | Token |
|---|---|
| Background | `#07080a` |
| Surface | `#101111` / `#1b1c1e` |
| Text | `#f9f9f9` / `#9c9c9d` / `#6a6b6c` |
| Blocked / alarm / danger | `#FF6363` |
| Creative list / success | Raycast Green `#5fc992` |
| Escape button / tilde / interactive focus | Raycast Blue `#55b3ff` |
| Borders | `rgba(255,255,255,0.06)` / `#252829` |
| Type | Inter (add as downloadable font); body weight ~500; letterSpacing ≈ 0.01–0.02 em |
| Radii | 6–12dp cards/buttons; pills for primary CTAs |

**Alarm screen exception:** intentionally breaks the calm system — saturated emergency red, brutal type, motion, noise. Escape control remains the only calm element.

**Icons:** launcher + notification — tilde on near-black, cool accent. Drop paper/glass drawables.

Force dark look (replace current `MODE_NIGHT_NO` light glass theme).

### Error handling & edge cases

| Case | Behavior |
|---|---|
| Mic denied / SpeechRecognizer unavailable | Typed confession |
| Speech partial mismatch | Keep listening / show retry; do not unlock |
| Creative list empty on escape | Home |
| Creative list empty for confession sentence | Use fallback **“my creative work”** |
| Alarm already showing | Service/a11y no-op |
| Schedule flips to dormant mid-alarm | Finish alarm without granting; stop audio (user is outside enforcement) |
| Domain block while Chrome in foreground | Same alarm; give-in returns to Chrome |
| Strict timer expires mid-use | Immediate re-alarm next poll |
| Device media muted | Alarm stream still plays (USAGE_ALARM); vibration always runs with the alarm |
| Alarm volume at zero / playback inaudible | Aggressive full-countdown vibration; no attempt to override DND with special privileges |

### Testing strategy

**JVM unit tests (extend existing):**

- `AllowancePolicy` / visit end rules / schedule armed vs dormant  
- Confession string normalization  
- Stats aggregation (reclaimed, bought, streak)  
- `BlockMatcher` unchanged coverage  

**Emulator milestones (orchestrator):**

1. Theme shell on MainActivity  
2. Alarm visual+audio on blocked app  
3. Website → alarm path  
4. Strict vs once-per-visit  
5. Confession speak/type  
6. Active hours arm/disarm + notification  
7. Stats + receipt share  
8. Icons + store doc copy sanity  

### Delivery model

- Single continuous branch  
- Orchestrator owns: service, visit/allowance policy, alarm lifecycle, website path, confession unlock correctness  
- Composer 2.5 subagents: theme tokens/layouts, blocked-row controls, stats UI, receipt bitmap layout, store markdown/HTML, icon assets  
- Emulator verification after each milestone before next slice merges  

### Store / privacy updates

Update `docs/playstore/PRIVACY_POLICY.md`, `docs/privacy/index.html`, `STORE_LISTING.md`, `DECLARATIONS.md` to describe:

- Fire-alarm intervention (sound, vibration, full-screen)  
- Optional microphone for on-device confession; no recording/storage/transmission  
- Local-only stats and weekly receipt; share only on user action  

---

## File touch map (expected)

- Replace/retarget: `BlockedInterstitialActivity` → `AlarmActivity`, layouts, themes, colors, fonts, icons  
- Extend: `RedirectorService`, `UrlInterceptorService`, `TemporaryAllowanceManager`, `RedirectionManager` (last Creative / random), `MainActivity`, adapters, manifest permissions  
- Add: policy/visit/schedule/log/stats/confession/audio helpers + unit tests  
- Docs: playstore + privacy  

## Out of scope for this backport

- Multi-browser URL interception  
- Wear OS / widgets  
- Cloud backup of lists beyond Android Auto Backup rules already present  
- Changing package name or applicationId  
