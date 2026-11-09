# Changelog

## Unreleased

### Security

- The address bar no longer shows the "Secure connection" lock on a page whose certificate failed, and private tabs
  now show the "not secure" warning for http pages instead of hiding it behind the private-mode icon.
- The address bar shows only the site's host while not being edited, with the registrable domain emphasised and long
  hosts shortened from the start, so credentials (`https://bank.example@evil.example`) or long subdomains cannot hide
  the real site. Lists and tab titles also drop credentials from addresses.
- Screenshots and the recent-apps preview are blocked while the tab switcher can show private tab previews.
- "Allow ads on this site" is no longer offered in private tabs: it saved the site to the database, leaving a record
  of the private visit.
- Permission, open-app and download prompts ignore taps for half a second after appearing (tapjacking), and are
  withdrawn, with permission requests denied, when their tab leaves the screen.
- A camera-and-microphone request is only granted when both Android permissions are.
- `intent://` links that point other apps at `file:` or `content:` data, or back at Theta, are refused.
- Downloads: the prompt shows exactly the name the file is saved under; bidirectional and invisible characters are
  stripped, so `invoice<U+202E>fdp.apk` cannot pose as a PDF; and cookies are only attached when the download is on the
  same site as the page, because DownloadManager re-sends them on every redirect.
- Filter list downloads refuse to be redirected from https to http, and the size limit now counts bytes, so one
  endless line cannot exhaust memory.
- The crash report URL scrubber is installed in `Application.onCreate`, so crashes before the first frame are scrubbed.
- The browser UI only loads http(s) and about:blank addresses, as a guard against `javascript:` or `file:` arriving
  from stored data.
- Cosmetic filtering drops oversized page replies instead of parsing them on the main thread.

### Fixed

- With clear-on-exit on, a link opened from another app at cold start could be closed by the startup wipe.
- Reopening Theta from recent apps, or after the system restored it, reopened the last link from another app.
- A page that crashes its renderer on every load was reloaded forever; it now stops after a repeat and shows an error.
- A second file-upload request replaced the first, which then never got an answer.
- `testDebugUnitTest`, and so CI, now runs the ad blocker module's unit tests. The module tests its release build,
  and AGP only creates unit tests for the tested build type, so they were silently skipped.

### Changed

- The Neel theme is back, matching the launcher icon: indigo surfaces with a marigold accent in dark mode, deep indigo
  with marigold containers in light mode. Corners are slightly tighter throughout.
- The bottom toolbar is a floating dock with a filled new-tab button and a filled tab counter.
- The tab switcher has labelled page chips, picture-first cards with the title underneath, and a labelled "New tab"
  button.
- The new-tab page shows the theta mark and a slimmer privacy stats row. Site icons are circles.

## 0.2.0 (2026-11-08)

### Added in 0.2.0 after the UI rework

- Cookie pop-up blocking, on by default with its own switch in Shields and privacy: consent pop-up scripts are blocked
  and pop-ups that get through are hidden, without ever accepting cookies. Uses the EasyList Cookie List (CC BY 3.0),
  bundled and refreshed weekly with the other lists. Not counted in the privacy stats.
- One native ad unit per screen (new tab, downloads, bookmarks, history), so AdMob reports show what each earns.
- The app version lives in `app/version.properties`; `versionCode` is derived from it. Release steps are in
  `docs/RELEASING.md`, AdMob and store setup in `docs/STORES.md`.

### Removed in 0.2.0

- Full-screen (interstitial) ads, and the unused banner and interstitial ad unit settings. They stay out until the
  VPN feature.

### Added

- Gradle multi-module project on AGP 9.3, Kotlin 2.4 and Gradle 9.5, with convention plugins in `build-logic`, ktlint,
  detekt, GitHub Actions workflows, macrobenchmarks and a Baseline Profile.
- `core/common`: result types, injected dispatchers, address bar URL and search detection including Hindi domains.
- `core/analytics`: event allowlist, crash report URL scrubbing, consent-gated collection.
- `core/config`: Remote Config wrapper with validated values, kill switches and safe defaults.
- `core/data`: Room database, settings DataStore, repositories, one-tap local data erase.
- `core/webview`: locked security settings, service worker lockdown, navigation and intent policy, per-site permissions,
  incognito profile isolation, pool of at most 3 live WebViews, DownloadManager routing.
- `core/ads`: UMP consent before ads, interstitial frequency cap, placement and YouTube rules, native `AdSlot`.
- `core/ui`: the Neel theme, shared components, in-app language switching.
- `feature/adblock`: network filter engine, bundled EasyList, EasyPrivacy and IndianList snapshot, weekly update worker.
- `feature/browser`: tabs, address bar, tab switcher, incognito, find in page, desktop site, share, text size, lite mode,
  site permission, app link and download prompts, fullscreen video, file uploads.
- `feature/newtab`, `feature/history`, `feature/bookmarks`, `feature/downloads`, `feature/settings`, first-launch consent.
- English, Hindi, Marathi and Russian strings. The translations were machine written and need a native speaker's review.

### Added in the UI and UX rework

- Slate theme in dark and light, with an "Increase contrast" option, and a new line icon set (Lucide, ISC licence).
- Tab switcher as a grid of page previews, with separate private and tab group views and a menu for new tab, new
  private tab, new tab group, close all tabs, select tabs, delete browsing data and settings.
- Tab groups with names and nine colours: select tabs to group them, add a tab to a group from the page menu, open a
  long-pressed link in a new tab in a group, rename, recolour, ungroup or close a group. Groups are saved with tabs
  (database version 2, migrated automatically).
- Long-press menu for links: open in new tab, in a group or in a private tab, copy or share.
- New-tab page with a bundled background photo that changes on every new tab, credited to its photographer, privacy
  stats (blocked ads and trackers, estimated data and time saved), top sites from the user's own history with real site
  icons, and recent sites. Privacy stats survive deleting browsing data and have their own reset.
- Settings rebuilt as grouped cards with search: Shields and privacy, new tab page, search engine, tabs, site settings
  (autoplay, site permissions, ad exceptions), appearance, accessibility, languages, clear browsing data (choose what
  to clear), about, open-source licences and photo credits.
- Accessibility: page text size with a live sample, A+ and A- zoom buttons over pages, increased contrast.
- Autoplay can be allowed; it stays blocked by default.

### Changed

- The app size budget is now 50 MB (was 15 MB).
- The tab switcher no longer shows an ad: tab previews are web content, and ads stay away from web content.
- Builds against Android 17 (compileSdk 37), with core-ktx, Compose, lifecycle, navigation, Hilt and OkHttp on their
  latest releases. targetSdk stays at 36 until the ACCESS_LOCAL_NETWORK permission is approved.
- History groups visits by site, with a visit count, expandable rows and one-tap removal of a whole site. The choice
  is remembered.

### Fixed

- Native ad slots requested a new ad every time their screen reappeared, which stacked up AdMob validator bubbles
  and wasted data. Ads are now kept per placement and refreshed at most once a minute. The validator overlay is off.

### Fixed during development (each has a regression test)

- A new incognito session could see the previous session's cookies, because WebView cannot delete a loaded profile.
- The browser menu crashed on open.
- Typing after tapping the address bar added to the old address instead of replacing it.
- A link from another app that arrived during tab restore was lost.
- Hindi domains such as भारत.भारत were treated as searches.
- The address parser computed the wrong path when credentials repeated the host.
