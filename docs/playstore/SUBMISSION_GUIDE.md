# Submission Guide — Creativitism on Google Play

This is a working document for getting Creativitism (`com.creativitism.appredirector`) into the Google Play Store. Read the whole thing before you start, especially the Policy Risk section near the end — it affects whether this is the right distribution channel for this app at all.

---

## 1. Costs

- **Google Play developer registration: $25, one time.** This is a per-account fee, not a per-app fee. It does not renew.
- **The app itself is free to install.** Nothing in this guide implies charging users. There is no in-app purchase, subscription, or ad SDK in the project.
- No other mandatory fees. (Optional: a small domain, if you don't want to host the privacy policy on GitHub Pages' default subdomain.)

Register at https://play.google.com/console/signup. You'll need a Google account, the $25 fee (card), and a government ID for identity verification (Play requires this for all new accounts as of 2023).

---

## 2. Personal account testing requirement (read this before you plan a timeline)

If you are registering a **new personal Google Play developer account** (created after November 2023), Google requires you to complete **closed testing with at least 12 testers, each opted in and active for at least 14 continuous days**, before you can apply for production access to release to the general public.

Practical implications:

- You need 12 people to accept a testing invite and actually open the app at least once (Play checks for opt-in activity, not just an accepted invite).
- The 14-day clock runs from when testing starts, not from when you hit 12 testers. Get testers opted in early.
- After 14 days with the tester threshold met, you submit an application for production access from within the Play Console, which is manually reviewed by Google (typically a few days).
- **Existing/organization accounts created before November 2023 are generally exempt from this specific requirement**, but still go through standard app review for every release.
- Plan for **roughly 3-4 weeks minimum** from account creation to a public production listing if you're starting a new personal account: account verification (can take a few days), closed test setup, the 14-day test window, the production-access review, and the standard release review.

Where to do this in Play Console: **Testing → Closed testing** → create a track, add tester emails (or a Google Group), share the opt-in link, wait for opt-ins, then track days-active in the track's dashboard.

---

## 3. Generating the upload keystore

Play uses **Play App Signing**: you upload your APK/AAB signed with an "upload key," and Google re-signs it with the actual app signing key it manages. You still need to generate and protect an upload keystore.

From the repo root, run:

```bash
keytool -genkeypair -v \
  -keystore creativitism-upload.jks \
  -alias creativitism-upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

`keytool` will prompt for a keystore password, a key password, and your name/org/locale details (these go into the certificate; they don't need to be accurate, but keep them consistent for your own records). `-validity 10000` gives roughly 27 years, which is standard practice for upload keys.

**Store the resulting `.jks` file and its passwords somewhere durable and private** (a password manager, or an encrypted backup) — outside the repo. If you lose the upload key entirely, you can still recover via Google's key-reset process because Play App Signing holds the real signing key, but it's an extra support flow you want to avoid.

### `keystore.properties`

The build (`app/build.gradle`) reads signing config from a file called `keystore.properties` at the **repository root** (not inside `app/`). It is **not committed** — `.gitignore` now excludes it explicitly (see the `keystore.properties` line added alongside the other keystore rules).

Create `keystore.properties` at the repo root with exactly these four keys:

```properties
storeFile=/absolute/or/relative/path/to/creativitism-upload.jks
storePassword=your-keystore-password
keyAlias=creativitism-upload
keyPassword=your-key-password
```

Notes:

- `storeFile` can be an absolute path or a path relative to the repo root — `build.gradle` resolves it via `rootProject.file(...)`.
- If this file is missing, `app/build.gradle` silently skips configuring the release `signingConfig`, so `bundleRelease` will produce an **unsigned** AAB. Always confirm the file exists before building for release.
- Never paste real values into a commit, a chat, or an issue tracker. If a password ever leaks, regenerate the keystore and treat the upload key as compromised (you can request Google reset the upload key association from within Play Console).

---

## 4. Building the release AAB

Google Play requires uploads in **Android App Bundle (.aab)** format, not raw APKs, for new apps.

From the repo root, with `keystore.properties` in place:

```bash
./gradlew bundleRelease
```

Output lands at:

```
app/build/outputs/bundle/release/app-release.aab
```

Sanity checks before uploading:

- Confirm `versionCode` and `versionName` in `app/build.gradle` are what you intend to ship (currently `versionCode 1`, `versionName "1.0.0"`). Every subsequent Play upload needs a strictly higher `versionCode`.
- Run `./gradlew bundleRelease --info | grep -i sign` (or just check the build log) if you want to confirm it picked up the release signing config rather than silently building unsigned.
- `minifyEnabled` is currently `false` in the release build type, so there's no ProGuard/R8 shrinking to worry about breaking things for this build. If you turn it on later, retest thoroughly before uploading.

---

## 5. Creating the app in Play Console

1. Play Console → **All apps → Create app**.
2. App name: `Creativitism`. Default language, app or game: App. Free. Confirm the developer declarations (compliance with US export laws, Play policies).
3. This creates the app shell and drops you into the **Dashboard**, which has a checklist of everything required before you can release. Work through it — the sections below map to the main ones.

### Store listing
Fill from `STORE_LISTING.md` in this folder: app name, short description, full description, category, tags, screenshots, icon, feature graphic. See that file for exact copy and asset specs.

### App content declarations (Play Console → Policy → App content)
This section has several sub-forms; all must be completed before you can go to production:

- **Privacy policy** — a URL. Host `PRIVACY_POLICY.md` (rendered as HTML) on GitHub Pages and paste that URL here. See `PRIVACY_POLICY.md` in this folder.
- **Ads** — declare "No, my app does not contain ads" (there is no ad SDK in this project).
- **App access** — declare all functionality is available without special access (no login required).
- **Content rating** — fill out the questionnaire. See `STORE_LISTING.md` for guidance; expect "Everyone."
- **Target audience and content** — select the age groups the app is intended for. This is a personal-use tool, not aimed at children; select 18+ or the general adult range as appropriate and answer "no" to appealing primarily to children.
- **Data safety** — the form asking what data is collected/shared. See `DECLARATIONS.md` for exact answers (short version: nothing is collected).
- **Government apps, Financial features, Health**, etc. — decline/not-applicable unless something changes.
- **Sensitive permissions / restricted permissions declarations** — this is the section that matters most for this app. You will be asked to justify `QUERY_ALL_PACKAGES`, and separately Play will flag the `AccessibilityService` usage and the Usage Access (`PACKAGE_USAGE_STATS`) permission for review. See `DECLARATIONS.md` for the exact text to paste into each form, and the Policy Risk section below before you commit to this path.

### Testing track and rollout
- Upload the AAB to a **Closed testing** track first (required regardless of account age, per Section 2 if you're on a new personal account).
- Once eligible, promote to **Production** and set a rollout percentage (start at 100% is fine for a first release with no prior install base).

---

## 6. Review timelines

- **First-time app review**: typically a few hours to a few days for straightforward apps. Apps that request sensitive permissions (accessibility, usage access, all-files/package visibility) are much more likely to be routed to **manual review**, which Google states can take up to **7 days**, and in practice can take longer if there's back-and-forth over a rejection.
- **Closed testing track review**: a lighter review than production but still happens; usually fast (hours).
- **Subsequent updates**: usually faster than the first review unless you change permissions or add new sensitive APIs, which can re-trigger full manual review.
- Rejections come with a policy citation and (sometimes) a specific reason. You can appeal or resubmit after fixing the cited issue; repeated rejections on the same policy area can lead to longer scrutiny on future submissions or, in the worst case, account-level enforcement.

---

## 7. Policy risk — read before you invest time in this path

Be honest with yourself about this section before you start the testing clock.

Creativitism uses three permission categories that Google Play treats as high-scrutiny:

1. **`AccessibilityService`, used to read the Chrome address bar.** Play's Accessibility API policy restricts this API to apps whose *core function* requires it for users with disabilities, or to a short list of explicitly allowed non-accessibility use cases (this app's use — reading the URL to block distracting websites — falls under the "app blocking / digital wellbeing" allowed use case Google has recognized in the past, but Google's enforcement here is inconsistent and has tightened over time). Expect this feature to trigger the **Restricted Permissions declaration form** and possibly a request for a **demo video** showing exactly how the feature is used. Rejection risk is real, not hypothetical — this is one of the most commonly rejected policy areas on Play, including for apps with a legitimate digital-wellbeing purpose.
2. **`QUERY_ALL_PACKAGES`**, used to list installed apps so the user can build their block/creative lists. This is also gated and requires an explicit declaration of core purpose. It's a more commonly approved use case (app pickers, launchers, and blockers are recognized) but still requires the form to be filled out correctly — see `DECLARATIONS.md`.
3. **`PACKAGE_USAGE_STATS` (Usage Access)**, used to detect the foreground app. Also gated, also requires a justification tied to core functionality.

Taken together, this app's permission profile matches an established, generally-approved category — **digital wellbeing / app blocker** — and there are existing apps in this category on Play (screen time managers, focus apps, website blockers). Approval is realistic. It is **not guaranteed**, and first-submission rejection on the accessibility usage specifically is a plausible outcome even for a compliant app, simply because Play's automated and manual reviewers are conservative about this API. Budget for at least one rejection-and-resubmit cycle.

### Fallback options if Play rejects the app

**Option A — ship app-blocking only, drop the website-blocking accessibility feature.**
Remove or gate off the `AccessibilityService` (`UrlInterceptorService`) and its manifest entry, and the accessibility disclosure flow. The core app-blocking flow (`PACKAGE_USAGE_STATS` + foreground detection + the pause/redirect interstitial) still works for apps; you lose per-website blocking inside the browser. This removes the single highest-risk policy surface and should substantially improve approval odds. This is a real code change, not just a settings toggle — plan it as a build variant or a feature flag if you want to keep both code paths available.

**Option B — distribute outside Google Play entirely.**
Build the release AAB/APK as described above, sign it with the same or a self-managed keystore, and publish it as a **GitHub Release** (a tagged release with the `.apk` attached, built via `./gradlew assembleRelease` for a directly-installable APK rather than an AAB, which only Play accepts). This is free, has no review process, and has no restrictions on `AccessibilityService`, `QUERY_ALL_PACKAGES`, or Usage Access use. Users install by enabling "install unknown apps" for their browser/file manager and sideloading the APK. Tradeoffs: no Play Store discoverability, no automatic updates (users need to be notified and reinstall manually, or you build a simple in-app update check), and every user has to actively opt into sideloading, which limits reach to a technical/trusting audience. This is a reasonable fallback for a personal or niche-audience tool and something you can do in parallel with a Play submission attempt at essentially no cost.

Nothing above is a reason not to try Play — the category is legitimate and approved apps like it exist — but go in expecting friction on the accessibility permission, and have Option A or B ready rather than being surprised by a rejection.
