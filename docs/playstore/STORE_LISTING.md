# Store Listing — Creativitism

Copy for the Play Console "Store listing" page, plus asset specs and content-rating guidance. Character counts are called out because Play enforces hard limits.

---

## App name

```
Creativitism
```

---

## Short description

Limit: 80 characters. Play Console will reject anything longer.

```
Trade doomscrolling for creating. A pause before the apps that pull you in.
```

Character count: 75 (verify in Play Console's live counter before saving — it counts the exact string you paste, including trailing spaces).

---

## Full description

Limit: 4000 characters.

```
Some apps are where you make things. Some apps are where time goes and doesn't come back. Creativitism doesn't try to guess which is which — you tell it, once, and it remembers.

Sort the apps and sites on your phone into two short lists: Blocked, for the ones that pull you in without giving much back, and Creative, for the ones where you actually make or build something. Everything else is left alone.

When you open something on your Blocked list, Creativitism doesn't lecture you and doesn't lock you out. It gives you a moment. A full-screen pause appears — "Take a breath" — and asks you to wait ten seconds before doing anything else. Ten seconds is not a punishment. It's just long enough to notice you're about to do the thing on autopilot, and to decide on purpose instead.

After the pause, you choose. You can be sent straight to one of your Creative apps or sites instead — the sketchbook, the notes app, the instrument, whatever you've told Creativitism you'd rather be doing. Or you can go ahead anyway and use the blocked app for five minutes. No shame, no streaks, no guilt copy. Five minutes, on the clock. When the five minutes are up, you're paused again, and you choose again.

That's the whole mechanism. There's no scoring, no leaderboard, no notifications nagging you back into the app to check your "progress." Creativitism isn't trying to gamify your attention — it's trying to interrupt the part where you don't notice you've handed it over.

Under the hood, Creativitism watches which app is in front so it knows when to step in, and — if you turn on website blocking — reads the address bar in your browser so it can catch specific sites, not just apps. Both of these only ever run the check "is this on my own list?" Nothing about what you look at, type, or do is collected, sent anywhere, or stored outside your phone. There's no account to make, no server to talk to, no analytics, no ads. Your two lists live in this app's local storage and nowhere else.

Creativitism doesn't promise to fix your relationship with your phone. It just puts a small, deliberate pause where there used to be none, and leaves the rest of the decision to you.
```

Character count: 2,173 (well under the 4,000 limit — leaves room to expand later without a rewrite).

---

## Category

Primary suggestion: **Productivity**.

Alternative worth considering: **Health & Fitness**, under its Digital Wellbeing framing — Play has increasingly grouped screen-time and focus tools there. If Play's category picker offers a "Tools" or "Lifestyle" option in your region, either is a defensible fallback, but Productivity is the closest match to what the app actually does (redirect attention toward creative work) and is the safer default.

---

## Tags / search terms

Play Console lets you pick a small number of descriptive tags in some flows; where free-text keywords are relevant (e.g. in the short description or future ASO work), use terms like:

```
digital wellbeing, screen time, app blocker, focus, distraction, productivity, mindfulness, habit, website blocker
```

Don't keyword-stuff the visible description — Play's spam policy explicitly penalizes that. Keep tags/keywords in metadata fields, not repeated in the description copy.

---

## Content rating questionnaire

Play routes content rating through IARC (International Age Rating Coalition) — you answer a questionnaire in Play Console → Policy → App content → Content ratings, and it computes ratings for each region automatically.

Expected answers for Creativitism, given what the app actually does (no user-generated content shared with others, no violence, no in-app purchases, no gambling, no user communication features, no ads):

- Violence: None.
- Sexual content: None.
- Profanity/crude humor: None.
- Controlled substances (alcohol/tobacco/drugs references): None.
- Gambling (simulated or real): None.
- User-generated content shared with or visible to other users: No — the block/creative lists are private and local to the device, not shared or visible to anyone else.
- Users can interact/communicate with each other: No.
- Location sharing: No.
- Personal information shared with third parties: No.
- Digital purchases: No.

Expected outcome: **Everyone** (or the closest regional equivalent — PEGI 3, USK 0, etc.) across all rating boards, since nothing in the questionnaire trips a higher tier.

---

## Required graphic assets

Play Console will not let you publish without these. Specs per Play's current requirements:

| Asset | Requirement | Status in this repo |
|---|---|---|
| App icon | 512 x 512 px, 32-bit PNG, no alpha channel required by the listing (transparency is fine, Play flattens it for some surfaces) | `docs/playstore/assets/icon_512.png` present |
| Feature graphic | 1024 x 500 px, JPG or 24-bit PNG, no alpha | `docs/playstore/assets/feature_graphic_1024x500.png` present |
| Phone screenshots | Minimum 2, up to 8. JPG or 24-bit PNG. Each dimension between 320px and 3840px; aspect ratio between 16:9 (landscape) and 9:16 (portrait) — i.e. standard portrait phone screenshots (e.g. 1080x1920 or 1080x2340) are fine | **Not yet in repo — capture from a running device/emulator before submission** |
| Tablet screenshots (7" / 10") | Optional but recommended if you want tablet listing visibility; same format rules | Not required for a phone-first app; skip unless you test on tablet |
| Promo video | Optional (YouTube URL) | Skip unless produced separately |

### Suggested screenshots to capture (once the app is running on a device)

1. Home screen showing the Blocked and Creative lists.
2. "Your apps" picker used to add an app to a list.
3. The "Take a breath" pause screen (interstitial) mid-countdown.
4. The choice screen after the pause: redirect vs. use for 5 minutes.
5. Protection status card showing protection is on.

Capture at a phone's native resolution (e.g. via Android Studio's emulator or `adb shell screencap`), crop to remove system status-bar clutter if desired, and save as PNG into `docs/playstore/assets/` before uploading to Play Console.
