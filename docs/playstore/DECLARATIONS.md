# Play Console Declarations — Exact Answers

Copy-paste text for the specific Play Console forms that gate sensitive permissions and data handling. Read `SUBMISSION_GUIDE.md` section 7 (Policy Risk) before submitting these — approval is not guaranteed even with accurate, well-written declarations.

All of this is written to be true and specific to what the app actually does. Do not soften or genericize it when pasting — Play reviewers (and the automated screening that runs first) respond better to specific, checkable claims than to generic boilerplate.

---

## (a) Sensitive permission declaration — `QUERY_ALL_PACKAGES`

Play Console path: **Policy → App content → Permissions declaration form** (triggered automatically once the manifest is detected to request `QUERY_ALL_PACKAGES`).

**Core use case to select:** "App discovery, including displaying a list of all apps installed on the device" / a device search, launcher, antivirus, file manager, or app-management use case — pick whichever option in the current form most closely matches "app blocker / digital wellbeing tool that lets the user pick apps from their installed list." Play's category list changes wording periodically; select the closest match to app-management/blocking, not "unrelated" or "other."

**Justification text to paste:**

```
Creativitism is a digital-wellbeing app blocker. Its core function is to let the
user build two personal lists — apps they want blocked and apps they want to be
redirected to instead — by choosing from the apps installed on their own device.
QUERY_ALL_PACKAGES is used exclusively to populate this in-app app picker with
the names and icons of installed apps so the user can select which ones to add
to their lists. The resulting list is never transmitted, shared, or used for any
purpose other than displaying it back to the same user for selection. No package
list, usage data, or app metadata is collected, logged, or sent off the device.
```

**Demo/screenshot if requested:** the "Your apps" screen (`all_apps_title` / `all_apps_subtitle` in the app) showing the installed-app picker with buttons to add an app to Blocked or Creative.

---

## (b) AccessibilityService declaration + in-app prominent disclosure

Play Console path: **Policy → App content → Accessibility → Permissions declaration form** (triggered by the `<service>` with `android.accessibilityservice.AccessibilityService` in the manifest, specifically `UrlInterceptorService`).

### Core use case to select

Pick the option closest to **"App blocking / usage limiting"** or **"Assist users with tasks by interacting with the screen on their behalf for a non-accessibility purpose that Play explicitly permits"** — current Play forms typically list a category along the lines of "Helping users manage screen time / block distracting content" under allowed non-a11y uses. If the form asks you to distinguish between serving users with disabilities vs. another permitted use, select the latter and specify digital wellbeing / content blocking.

### Justification text to paste

```
Creativitism uses the AccessibilityService API for a single, narrow purpose:
reading the URL currently displayed in the user's browser address bar (Chrome
only — see the accessibility_service_config.xml packageNames restriction) so it
can be compared, entirely on-device, against a list of websites the user has
personally chosen to block. When the current URL matches an entry on the user's
own list, the app shows a pause screen, consistent with its core function as a
digital-wellbeing app blocker.

The service does not read, log, or transmit page content, form input, other
app content, or any other on-screen text. It targets only the browser package
and only the address bar. No data read by this service leaves the device, is
stored beyond the immediate comparison, or is shared with any third party.

This use falls under Play's permitted use case for blocking or limiting access
to distracting content as part of a digital wellbeing / parental-control-style
tool, which is the app's entire purpose.
```

### In-app prominent disclosure (already implemented — reference only)

Play requires that an app using the Accessibility API for a non-accessibility purpose show, inside the app itself before the feature is enabled, a clear disclosure of what the service does. This is already implemented in `app/src/main/res/values/strings.xml` (`accessibility_disclosure_title`, `accessibility_disclosure`) and shown to the user before they're sent to Android's Accessibility settings to turn the service on. The exact text, for reference when filling out the declaration form (some forms ask you to paste the in-app disclosure text verbatim):

```
Website blocking uses accessibility

Creativitism uses Android's AccessibilityService API to read the web address
shown in your browser's address bar. This is only used to check the address
against your own blocked list and pause the page when it matches.

Creativitism does not collect, store, or share this data, and never reads any
other content from your screen. You can turn this off anytime in Accessibility
settings.
```

If the Play form asks for a demo video: record the flow from the app's "Turn on website blocking" action through the disclosure screen through the Android Accessibility settings toggle, and show a blocked site actually getting paused. Reviewers for this category frequently request video evidence — budget time for this before submitting.

---

## (c) Usage Access (`PACKAGE_USAGE_STATS`) justification

Play Console path: **Policy → App content → Permissions declaration form** for Usage Access (triggered by the manifest's `PACKAGE_USAGE_STATS` permission).

**Core use case to select:** "Parental control apps" / "Digital wellbeing" / "App usage/screen time management" — whichever label the current form uses for apps that need to know the foreground app to manage or limit usage.

**Justification text to paste:**

```
Creativitism needs to detect which app is currently in the foreground in order
to recognize the moment the user opens an app on their own Blocked list, so it
can show the pause screen before the app continues to load. This is the entire
purpose of the permission: a real-time check of "is this app one the user asked
to be paused for," performed on-device. Creativitism does not log, store, or
transmit a history of which apps the user opens or when. No usage statistics
are retained beyond the immediate check needed to trigger (or not trigger) the
pause screen.
```

---

## (d) Foreground service (`specialUse`) justification

Play Console path: this is declared directly in the manifest via the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` metadata on `RedirectorService`, and may also surface in Play Console's pre-launch report or a policy questionnaire if Play requests confirmation of `FOREGROUND_SERVICE_SPECIAL_USE` usage.

**Text already in the manifest** (`AndroidManifest.xml`, on the `RedirectorService` special-use property) — reuse this exact wording if a console form asks you to restate it:

```
Digital-wellbeing app blocking: monitors the foreground app so the user's
self-selected distracting apps can be intercepted and redirected to creative
alternatives.
```

If Play Console's Foreground Service (Special Use) declaration form asks for a longer justification, use:

```
This foreground service continuously checks which app is in the foreground so
that Creativitism can intercept apps the user has personally added to their
Blocked list and show a pause/redirect screen before the app opens. This is a
core, user-initiated function of a digital-wellbeing app blocker and cannot be
achieved reliably with a non-foreground service, since Android aggressively
suspends background processes that aren't running as a foreground service.
The associated persistent notification ("Creativitism is protecting you")
keeps this visible to the user at all times while active, and the user can
turn protection off from within the app at any time.
```

---

## (e) Data safety form

Play Console path: **Policy → App content → Data safety**. This is the form that produces the "Data safety" section shown on the public store listing.

Answer every data-type section the same way, since the app collects nothing:

**Does your app collect or share any of the required user data types?**
```
No
```

If the form still steps through each category individually (Location, Personal info, Financial info, Health and fitness, Messages, Photos/videos, Audio files, Files and docs, Calendar, Contacts, App activity, Web browsing, App info and performance, Device or other IDs) — answer **"Data is not collected"** for every single one. This is accurate: the app reads a foreground app package name and a URL transiently to compare against a local list, but never collects (i.e., never stores beyond immediate use, logs, or transmits) any of it in the sense Play's form means by "collect."

**Is all user data encrypted in transit?**
```
Not applicable / No data is transmitted
```
If the form forces a Yes/No with no "not applicable" option, select "No" and use the free-text explanation field (if offered) to state: "The app makes no network requests and transmits no data anywhere, so there is no data in transit to encrypt."

**Do you provide a way for users to request that their data be deleted?**
```
Yes
```
Explanation text:
```
All data created by Creativitism (the user's Blocked and Creative lists) is
stored locally on the user's device only. Users can delete it at any time via
Android Settings → Apps → Creativitism → Storage → Clear data, or by
uninstalling the app. No server-side or account-linked data exists to delete
separately, because none is collected or stored off-device.
```

**Data types collected (if the form requires you to positively state something rather than leave categories blank):**
```
None. Creativitism collects no user data. It stores two user-created lists
(blocked apps/sites, creative apps/sites) locally in Android SharedPreferences
on the user's device, which never leaves the device.
```

**Security practices section (if present):**
- "Data is encrypted in transit": leave unchecked / not applicable — nothing is transmitted.
- "You can request that data be deleted": checked, per above.
- "Committed to following the Play Families Policy" or similar: not applicable unless targeting children, which this app does not.

Link the Data safety form to the hosted `PRIVACY_POLICY.md` URL (see `SUBMISSION_GUIDE.md` section 5) — Play cross-checks that the privacy policy content is consistent with the Data safety answers, and this document is written to match exactly.
