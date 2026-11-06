# Changelog

## Unreleased

### Added

- Gradle multi-module project on AGP 9.3, Kotlin 2.4 and Gradle 9.5, with convention plugins in `build-logic`, ktlint,
  detekt and GitHub Actions workflows.
- `core/common`: result types, injected dispatchers, address bar URL and search detection including Hindi domains.
- `core/analytics`: event allowlist, crash report URL scrubbing, consent-gated collection.
- `core/config`: Remote Config wrapper with validated values, kill switches and safe defaults.
- `core/data`: Room database, settings DataStore, repositories, one-tap local data erase.
- `core/webview`: locked security settings, service worker lockdown, navigation and intent policy, per-site permissions,
  incognito profile isolation, pool of at most 3 live WebViews, DownloadManager routing.
- `core/ads`: UMP consent before ads, interstitial frequency cap, placement and YouTube rules.
- `feature/adblock`: network filter engine, bundled EasyList, EasyPrivacy and IndianList snapshot, weekly update worker.

### Measured

- Filter matching against 111,371 rules, non-debuggable build on an emulator: median 13 us, p99 130 us per request.
  Real budget device measurement is still to do.
