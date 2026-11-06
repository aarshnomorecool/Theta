# CLAUDE.md

Guidance for Claude Code when working in this repository. Read this fully before making changes.

## Project

`Theta` is a lightweight Android browser for Indian users on budget phones. It is monetised with ads, and a built-in VPN/proxy and a media downloader will be added later.

- **Audience:** India first, then russian. English, Hindi and Russian. Typical device is 3 to 4 GB RAM on an Android 10 to 14 budget phone, often on patchy 4G or public Wi-Fi.
- **Positioning:** fast, small, private, and works on cheap phones and cheap data.
- **Owner:** Team Seasaw. The publisher is a registered business with a Google Play Organization account.
- **Package:** `in.<company>.browser` (placeholder, replace before first release).

## Roadmap

Only build what belongs to the current phase. If a task drifts into a later phase, stop and ask.

| Phase | Scope | Status |
|---|---|---|
| 1. Browser MVP | Tabs, incognito, search, history, bookmarks, new-tab page, file downloads, ad/tracker blocking, dark mode, settings, ads on native screens | **CURRENT** |
| 2. VPN / proxy | Browser-only proxy using `ProxyController`, phone OTP gate, server list via Remote Config, coins for premium countries | Not started |
| 3. Media downloader | Direct media file downloads with a server-side blocklist | Not started |
| 4. Rewards | Coins wallet, offerwall, vouchers, Play Integrity | Not started |

### Phase 1 feature checklist
- [ ] Address bar with URL/search detection and a search engine picker
- [ ] Multiple tabs, tab switcher, restore tabs after process death
- [ ] Incognito tabs using a separate WebView Profile, wiped when closed
- [ ] New-tab page: speed dial, recent sites, one native ad slot
- [ ] History and bookmarks (local only)
- [ ] File downloads through Android `DownloadManager`
- [ ] Ad and tracker blocking from filter lists
- [ ] Dark mode, including dark web pages through algorithmic darkening
- [ ] Find in page, desktop site toggle, share, text size
- [ ] "Lite mode" toggle that blocks images and heavy media to save data
- [ ] Settings: clear data, clear on exit, default browser prompt, language
- [ ] Hindi and Marathi translations for all UI strings
- [ ] Crash reporting, privacy-safe analytics, Remote Config kill switches

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin (latest stable), Java 17 toolchain |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Browser engine | Android System WebView + `androidx.webkit` (do **not** add GeckoView without approval; it adds about 50 MB) |
| Architecture | MVVM with unidirectional state (`StateFlow` + immutable UI state) |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Local DB | Room (tabs, history, bookmarks, downloads) |
| Settings | Proto or Preferences DataStore |
| Background work | WorkManager (filter list updates, cleanup) |
| Images | Coil (favicons, tab thumbnails) |
| Serialization | kotlinx.serialization |
| Networking | OkHttp (filter list downloads only) |
| Crashes / analytics / flags | Firebase Crashlytics, Firebase Analytics, Firebase Remote Config |
| Ads | Google Mobile Ads SDK (AdMob) + User Messaging Platform (UMP) |
| Play services | Play In-App Updates, Play In-App Review |
| Later phases | Play Integrity API, Play Billing, Supabase (accounts/rewards backend) |
| Lint / format | ktlint, detekt, Android Lint |
| CI | GitHub Actions |

- **SDK levels:** `minSdk = 26`, and `targetSdk` and `compileSdk` set to the level Google Play currently requires.
- **Dependencies:** keep them in `gradle/libs.versions.toml`. Never hardcode versions in module build files.

## Module layout

```
app/                    Application, MainActivity, nav graph, Hilt setup
core/common/            Result types, dispatchers, extensions, URL utils
core/data/              Room DB, DAOs, DataStore, repositories
core/ui/                Theme, typography, shared Compose components
core/webview/           WebView factory, settings, clients, profile handling
core/ads/               AdMob init, UMP consent, ad slot composables, frequency caps
core/analytics/         Analytics wrapper (the ONLY place events are sent)
core/config/            Remote Config wrapper and defaults
feature/browser/        Browser screen, tabs, address bar, tab switcher
feature/newtab/         New-tab page, speed dial
feature/history/
feature/bookmarks/
feature/downloads/
feature/adblock/        Filter list parsing, matching engine, update worker
feature/settings/
# later
feature/vpn/            Phase 2
feature/downloader/     Phase 3
feature/rewards/        Phase 4
```

**Dependency rules:**
- `feature/*` may depend on `core/*`, never on another feature.
- `core/*` never depends on `feature/*`.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # install on connected device
./gradlew testDebugUnitTest             # unit tests
./gradlew connectedDebugAndroidTest     # instrumented + Compose UI tests
./gradlew lint ktlintCheck detekt       # static checks (must pass before commit)
./gradlew ktlintFormat                  # auto-format
./gradlew :benchmark:connectedBenchmarkAndroidTest   # macrobenchmarks
./gradlew :app:generateBaselineProfile  # regenerate baseline profile
./gradlew bundleRelease                 # release AAB (CI only, needs signing secrets)
```

**Before saying a task is done:**
1. Run `lint ktlintCheck detekt testDebugUnitTest`.
2. Make sure `assembleDebug` succeeds.

## Coding conventions

- **State:** one `UiState` data class per screen, exposed as `StateFlow`. User actions go in as sealed `UiEvent` calls. No mutable state is exposed from ViewModels.
- **Threading:** no blocking work on the main thread. Inject dispatchers; never hardcode `Dispatchers.IO`.
- **Compose:**
  - Composables are stateless where possible.
  - Hoist state.
  - Every screen has a `@Preview` in light, dark and Hindi locale.
- **Strings:**
  - No hardcoded user-facing strings. Everything lives in `strings.xml` with `en`, `hi` and `mr` versions.
  - If a translation is missing, add the English string and leave a `TODO(translate)` comment.
- **Errors:** expected failures are modelled as results, not thrown exceptions. Never swallow exceptions silently.
- **Naming:** `FooScreen`, `FooViewModel`, `FooRepository`, `FooDao`, `FooEntity`.
- **Comments:** explain *why*, not *what*. No commented-out code in commits.
- **Size:** keep files under about 400 lines; split before that.

## WebView rules (security critical)

All WebView setup lives in `core/webview`. Never configure a WebView anywhere else.

**Feature checks**
- Check `WebViewFeature.isFeatureSupported(...)` before every `androidx.webkit` call, and degrade gracefully.

**Settings that must stay locked**
- Safe Browsing stays **enabled**.
- `allowFileAccess = false`, `allowContentAccess = false`.
- `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`.
- **Never** call `handler.proceed()` in `onReceivedSslError`. Cancel, then show an error page.
- **Never** add `addJavascriptInterface` to WebViews that load third-party pages.

**Links and intents**
- Handle `intent://`, `market://`, `upi://`, `tel:` and other non-http schemes in `shouldOverrideUrlLoading`.
- Open other apps only after asking the user. Never auto-launch.

**Site permissions**
- Ask per site for camera, mic and location.
- Remember the decision in Room.
- Incognito never remembers decisions.

**Incognito**
- Uses a separate `Profile` from `ProfileStore` (MULTI_PROFILE feature).
- Closing all incognito tabs deletes that profile's data.
- If MULTI_PROFILE is unsupported, fall back to clearing cookies, cache and storage when incognito tabs close, and show a notice.

**Memory**
- Keep at most 3 live WebViews.
- Background tabs are saved with `saveState` and destroyed, then restored on demand.
- Always call `destroy()` on discarded WebViews.

**Downloads (Phase 1)**
- Route `DownloadListener` to `DownloadManager`.
- Do **not** sniff or extract media streams. That is Phase 3 and has its own rules.

**Dark mode**
- Use algorithmic darkening through `WebSettingsCompat` when the app theme is dark.

## Ads and monetisation rules (hard rules)

Breaking these can get the AdMob account banned or the app pulled. Treat them like failing tests.

1. **No ad may be visible on screen while third-party web content is visible.** Ads go only on native screens:
   - New-tab page
   - Tab switcher
   - Downloads list
   - Bookmarks and history lists

   This prevents "framed content" violations.
2. **Never inject ads into web pages, and never modify ads on web pages.**
3. **The ad blocker must not block or interfere with our own AdMob ads.** Its scope is WebView requests only.
4. **Interstitials:**
   - Only at native-to-native transitions, for example closing the tab switcher back to the new-tab page.
   - Never on app launch in the first session.
   - Never while a page is loading.
   - Frequency cap from Remote Config, default max 1 every 4 minutes.
5. **No accidental clicks:** no ads next to tappable controls like the address bar or tab buttons, and no ads that move content when they load.
6. **No YouTube next to ads:** never show an ad on any screen where a YouTube video or YouTube page is visible.
7. **Rewarded ads (Phase 2+):**
   - Shown only after an explicit opt-in tap.
   - Rewards are in-app only (coins, ad-free time, premium servers). Never cash or vouchers.
   - Verify with AdMob server-side verification.
8. **Consent:** initialize UMP consent before loading any ad.
9. **Test ads only:** use test ad unit IDs in debug builds. Real IDs come from `local.properties` / CI secrets and must never be committed.
10. **Code location:** all ad code lives in `core/ads`. Features use the `AdSlot` composable and never call the Ads SDK directly.

## Privacy and compliance rules

- **Browsing data stays on the device.** History, bookmarks, tabs, cookies and downloads are never uploaded.
- **Analytics events must never include:**
  - URLs, domains, page titles or search queries
  - Typed text
  - Device identifiers beyond what Firebase collects by default

  Every event goes through `core/analytics`, which enforces an allowlist of event names and parameters.
- **Crash reports:** strip URLs and query strings from logs and breadcrumbs before they reach Crashlytics.
- **No new SDKs without approval:** do not add any third-party SDK without approval, and update the Play Data safety notes in `docs/data-safety.md` whenever data collection changes.
- **DPDP Act:** keep a clear in-app privacy notice and consent screen on first launch. Users can delete all local data from Settings in one tap.
- **Privacy policy:** the privacy policy URL lives in Remote Config and on the website. Keep it in sync with actual behaviour.
- **Filter lists:** credit EasyList / EasyPrivacy in Settings > About, as their licence requires.
- **Phase 2 VPN (do not build yet, but don't design against it):**
  - The proxy must go through `ProxyController` (browser traffic only).
  - The VPN requires phone OTP login.
  - Servers keep only the minimum records required by CERT-In rules (account phone number, assigned IP, connection timestamps), and **never browsing history**.
  - Do not use `VpnService` without approval.
- **Phase 3 downloader (do not build yet):**
  - Direct media files only.
  - Never bypass DRM or decrypt protected streams.
  - Server-side blocklist that includes YouTube and the major streaming and social platforms.
  - No platform names or logos in UI or store listing.

## Performance budgets

These are release blockers. Check them with macrobenchmarks on a low-end test device.

| Metric | Budget |
|---|---|
| Play download size (AAB) | under 15 MB |
| Cold start to usable new-tab page | under 1.2 s |
| Idle memory with 1 tab | under 180 MB |
| Live WebViews | max 3 |
| Jank during scroll | under 5% janky frames |

- Ship and regenerate a Baseline Profile whenever startup code changes.
- Prefer R8 full mode and resource shrinking in release builds.
- Lazy-load anything not needed for the first frame (ads, Remote Config fetch, filter list updates).

## Ad blocking engine

- **Lists:** EasyList + EasyPrivacy, plus an India-specific list if one is available and its licence allows it.
- **Bundled lists:**
  - Parse network rules into an efficient matcher (domain trie plus pattern rules).
  - Ship a pre-compiled snapshot in assets so the first run works offline.
- **Updates:**
  - A `WorkManager` job refreshes the lists weekly, on unmetered network only.
  - The list URLs come from Remote Config.
- **Blocking:** block inside `shouldInterceptRequest` by returning an empty response.
- **Speed:** matching must stay under 1 ms per request on the test device. Add a benchmark.
- **Settings:** per-site "allow ads on this site" toggle, stored in Room.

## Testing

- **Unit tests:** ViewModels, repositories, URL parsing, the filter matcher and frequency caps. Use JUnit4, MockK, Turbine and coroutine test dispatchers.
- **Room:** in-memory DB tests for every DAO.
- **UI:** Compose UI tests for the address bar, tab switcher and settings.
- **WebView:** instrumented tests with a local `MockWebServer` for navigation, SSL error handling, incognito isolation and blocking.
- **Benchmarks:** startup and scroll macrobenchmarks in the `:benchmark` module.
- **Bug fixes:** every bug fix gets a regression test.

## Definition of done

- [ ] Feature works in light, dark, English, Hindi and Marathi
- [ ] Works offline or fails gracefully with a friendly message
- [ ] Lint, ktlint, detekt and unit tests pass
- [ ] No new strings hardcoded, no secrets committed
- [ ] Ads and privacy hard rules above still hold
- [ ] Performance budgets not regressed
- [ ] `docs/CHANGELOG.md` updated

## Git and CI

- **Branches:** `main` is always releasable. Work happens on `feat/...`, `fix/...` or `chore/...` branches.
- **Commit messages:** Conventional Commits, e.g. `feat(browser): add tab restore`, `fix(adblock): handle wildcard rules`.
- **Pull request CI (GitHub Actions):** lint, ktlint, detekt, unit tests and `assembleDebug`.
- **Tag CI (`v*`):** `bundleRelease`, signed with secrets and uploaded to Play internal testing.
- **Secrets:** never commit `google-services.json` for prod, keystores, AdMob IDs or API keys. Use `local.properties` and GitHub Secrets.

## Never do this

- Never add `VpnService`, media stream extraction, rewards or cash payouts before their phase.
- Never place ads next to web content, inside web pages, or on YouTube screens.
- Never send URLs, searches or browsing history off the device.
- Never call `proceed()` on SSL errors or disable Safe Browsing.
- Never add a dependency, SDK or permission without stating why and getting approval.
- Never request `QUERY_ALL_PACKAGES`, SMS, call log or accessibility permissions.
- Never commit real ad unit IDs, keys or keystores.
- Never use platform names or logos (YouTube, Instagram, etc.) in UI or store text.

## Open decisions

- Final app name and package
- Default search engine and whether to pursue a search revenue partnership later
- Whether to add a lightweight news feed on the new-tab page (more ad inventory, but more scope)
- VPN server regions for Phase 2 (planned: Oracle Cloud Singapore free tier first)