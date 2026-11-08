# Releasing Theta

How to ship a version, and every update after it. Follow it top to bottom each time.

## Decide once, before the very first upload

These can never change after the first store upload:

1. **Package name** (`applicationId` in `app/build.gradle.kts`), currently the placeholder `in.seasaw.browser`.
   Stores, AdMob and every user's installed copy are tied to it. Pick the final one first: a reversed domain you own
   (for example `in.yourdomain.theta`).
2. **Signing key.** Every update must be signed with the same key as the first upload, or phones refuse to install
   it. Create it once (step 3 below) and keep **two backups off this computer**, for example an encrypted USB drive
   and a password manager. If the key is lost, the app can never be updated again in Indus or other non-Play stores.

## Version numbers

The version lives in one place: `app/version.properties`.

```
versionName=0.2.0
```

`versionCode`, which stores use to tell versions apart, is worked out from it as `major * 10000 + minor * 100 + patch`
(0.2.0 is 200, 1.4.12 is 10412). Stores refuse an upload whose `versionCode` is not higher than the previous one, so
versions must only go up.

| Change | Bump | Example |
|---|---|---|
| Bug fix only | patch | 0.2.0 to 0.2.1 |
| New features | minor | 0.2.1 to 0.3.0 |
| First public release, or a big redesign | major | 0.9.3 to 1.0.0 |

## Every release

1. **Update the version** in `app/version.properties`.
2. **Write the changelog.** In `docs/CHANGELOG.md`, rename `## Unreleased` to `## <version> (<date>)`, add a fresh
   empty `## Unreleased` above it, and keep the user-facing part short. Store "What's new" text comes from here.
3. **Refresh the bundled filter lists** (optional, but do it at least every few releases so new installs start fresh):
   ```
   ./gradlew :feature:adblock:engine:updateBundledFilters :feature:adblock:engine:updateBundledCookieList
   ```
4. **Run every check** and fix anything red:
   ```
   ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :feature:adblock:engine:test
   ```
   Then the device tests on an emulator (see `CLAUDE.md`).
5. **Put the real ad IDs in `local.properties`** (never committed), if not already there:
   ```
   ADMOB_APP_ID=ca-app-pub-...~...
   ADMOB_NATIVE_NEW_TAB_ID=ca-app-pub-.../...
   ADMOB_NATIVE_DOWNLOADS_ID=ca-app-pub-.../...
   ADMOB_NATIVE_BOOKMARKS_ID=ca-app-pub-.../...
   ADMOB_NATIVE_HISTORY_ID=ca-app-pub-.../...
   ```
   A release build stops with a clear error if any is missing, so a build without ads can never ship by mistake.
6. **Build signed files** in Android Studio: Build > Generate Signed App Bundle or APK.
   - **App bundle (.aab)** for Google Play.
   - **APK** for Indus Appstore and any store that does not take bundles, and for your website.
   - Choose your keystore and enter the passwords when asked. Leave "Remember passwords" off.
   - Output: `app/release/`.
7. **Install the signed APK on a real phone** and spend five minutes on it: open sites, tabs, a private tab,
   settings, the new-tab page. Release builds shrink code, which occasionally breaks something debug builds do not.
8. **Upload** to each store (see `docs/STORES.md`), with the "What's new" text from the changelog.
9. **Tag the release** in git once it is live: `git tag v0.2.0` then `git push origin v0.2.0`.

## The first time only: create the signing key

Android Studio: Build > Generate Signed App Bundle or APK > Create new.

- Key store path: somewhere outside the project folder, for example `D:\keys\theta-release.jks`.
- Validity: 50 years.
- Fill in your name and country.
- Back it up twice, as above. Never commit it, never email it.

On Google Play, turn on **Play App Signing** when asked: Google keeps the key that signs what users download, and
yours becomes an "upload key" that Google can reset if you lose it. Other stores use your key directly, so the backup
matters most for them.

## Updates later

- **Same package name and same key, always.** Phones only accept an update that matches both.
- **Roll out gradually on Google Play:** start at 10–20 % of users, watch for crashes for two or three days in
  Play Console, then go to 100 %. Other stores mostly release to everyone at once, so test more carefully there.
- **Database changes:** Room migrations must ship with a migration test (see `MigrationTest.kt`). A failed
  migration loses users' tabs.
- **Hotfix:** branch from the release tag, fix, bump the patch number, release, then bring the fix back to the main
  branch.

## If the app must be stopped quickly

Firebase Remote Config can switch off ads (`ads_enabled`) or the ad blocker (`adblock_enabled`) for everyone within
hours, without an update. Use it while a fixed version goes through store review.
