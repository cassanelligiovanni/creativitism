# Privacy Policy for Creativitism

**Effective date: July 22, 2026**

This policy applies to the Creativitism Android app (package name `com.creativitism.appredirector`). It is written in plain language on purpose. If anything here is unclear, contact us using the address at the bottom.

## The short version

Creativitism does not collect, transmit, store remotely, share, or sell any of your data. Everything the app knows about you stays on your device. There is no account, no server, no analytics, and no advertising in this app.

## What Creativitism does

Creativitism lets you mark apps and websites as either "Blocked" (things you'd rather spend less time on) or "Creative" (things you'd rather spend time on instead). When you open something on your Blocked list, the app shows a full-screen pause for ten seconds, after which you can choose to be redirected to something on your Creative list, or continue to the blocked app or site for five minutes before being paused again.

## Information we store

The only information Creativitism stores is the information you put into it directly: the list of apps and websites you've marked as Blocked, and the list you've marked as Creative. This is stored using Android's `SharedPreferences` mechanism, which writes a file in the app's private storage area on your device.

This data:

- Never leaves your device.
- Is not transmitted to us, to the app's developer, or to any third party.
- Is not backed up to any server we control (standard Android/Google device backup, if you have it enabled at the OS level, is controlled by you and Google, not by this app).
- Is not linked to your identity, an account, or an advertising ID, because none of those exist in this app.

## Permissions the app uses, and exactly why

Creativitism requests a small number of Android permissions. Each one exists for a single, narrow purpose, described below. None of them are used to collect data about you for any purpose other than the immediate on-device function described.

### Usage Access (`PACKAGE_USAGE_STATS`)

Used solely to detect which app is currently in the foreground on your device, so Creativitism can tell whether you've just opened something on your Blocked list. The app checks the foreground app locally, in real time, and does not log, store, or transmit your app usage history. We do not build a history of what you've opened; the check happens, a decision is made (pause or don't pause), and nothing about it is retained beyond what's needed for that immediate decision.

### Accessibility Service

Creativitism includes an optional Android `AccessibilityService`, used solely to read the web address currently shown in your browser's address bar (specifically, Chrome), so the app can compare that address against the website list you've built in the Blocked section. This is the only thing the service reads — it does not read page content, form fields, passwords, or anything else on screen, and it does not read any app other than the browser.

The comparison happens entirely on your device. The URL being checked is not collected, stored, or shared. It's read, compared against your own local blocklist, and discarded. Nothing from this process leaves the app or the device.

This matches the disclosure shown inside the app itself before you turn this feature on:

> "Creativitism uses Android's AccessibilityService API to read the web address shown in your browser's address bar. This is only used to check the address against your own blocked list and pause the page when it matches. Creativitism does not collect, store, or share this data, and never reads any other content from your screen. You can turn this off anytime in Accessibility settings."

You can disable this at any time in Android Settings → Accessibility, and website blocking will simply stop working until you turn it back on; app blocking (for apps, not websites) does not depend on this permission.

### Query All Packages (`QUERY_ALL_PACKAGES`)

Used solely to show you the list of apps installed on your device, so you can choose which ones to add to your Blocked or Creative lists. This permission lets the app see the list of installed app names and icons; it does not give the app access to those apps' data, and the list is only ever displayed to you, inside the app, for you to make selections from.

### Foreground Service permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`)

Used to run the background monitoring described above (foreground-app detection) reliably, in a way Android permits for extended periods. The associated notification ("Creativitism is protecting you") exists because Android requires foreground services to show one; it does not send or receive any data.

### Notifications (`POST_NOTIFICATIONS`)

Used only to show the persistent "protection is on" status notification and any local, on-device alerts related to the app's own function. No push notifications are received from a server, because Creativitism has no server.

### Display over other apps (`SYSTEM_ALERT_WINDOW`)

Used to show the "Take a breath" pause screen on top of a blocked app when it's opened. Purely a UI mechanism; involves no data collection.

### Boot / battery / wake permissions (`RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`)

Used to restart the protection service after your device reboots and to keep it running reliably without being stopped by battery optimization. These are operational permissions with no data-collection function.

## What we don't do

- We don't collect analytics of any kind (no Firebase Analytics, no crash reporting SDK, no third-party SDKs at all).
- We don't show ads or include any ad SDK.
- We don't require or offer an account, login, or sign-in.
- We don't have a backend server. The app makes no network calls, because it doesn't need to — everything it does is local to your device.
- We don't share, sell, rent, or otherwise disclose any information to third parties, because we don't collect any information to share in the first place.
- We don't use cookies, device identifiers for tracking, or advertising IDs.

## Children's privacy

Creativitism is not directed at children and does not knowingly collect information from anyone, including children, because it does not collect information from anyone at all.

## Your choices and data deletion

Because all data lives only in the app's local storage on your device, you control it directly:

- To remove an item from a list, remove it in the app.
- To erase everything the app has stored, go to Android Settings → Apps → Creativitism → Storage → Clear data, or simply uninstall the app. Either action permanently deletes the app's local data from your device; since nothing is stored anywhere else, this is a complete deletion.
- There is no remote account or server-side record to separately request deletion of, because none exists.

## Changes to this policy

If this policy changes — for example, if a future version of the app adds a feature that changes what data is read or how — we will update this document and change the effective date above. Since the app currently makes no network calls, updates to this policy will not be pushed to you automatically; check this page (linked from the app's Play Store listing) if you want the current version.

## Contact

Questions about this policy or the app's data practices can be sent to:

**cassanelligiovannia@gmail.com**
