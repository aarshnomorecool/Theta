# Changelog

## Unreleased

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

### Changed

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
