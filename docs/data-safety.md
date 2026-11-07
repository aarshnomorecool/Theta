# Play Data safety notes

Source of truth for the Play Console Data safety form. Update this whenever data collection changes (see `product.md`).

## Never leaves the device

History, bookmarks, tabs, tab groups, tab preview pictures, saved site icons, speed dial, cookies, site data, downloads,
site permission decisions, ad block exemptions and the privacy stats counter. Private tabs' previews and icons are kept
in memory only. The privacy stats counter holds totals only (blocked request count and the date counting began), never
which sites or requests; it survives "Delete all my data" by design and has its own reset in settings.
New-tab background photos ship inside the app, so showing them makes no network request.
Android cloud backup and device-to-device transfer are disabled for the whole app (`allowBackup=false`,
`data_extraction_rules.xml`), because they would otherwise copy this data off the device.

## Collected only after opt-in on the first-launch consent screen

Collection is off in the manifest, and is enabled from the user's stored consent choice in `DeferredStartup`.

| Data | SDK | Purpose | Notes |
|---|---|---|---|
| App interactions | Firebase Analytics | Analytics | Only the events and values in `EventAllowlist`. No URLs, domains, titles, queries or typed text. |
| Crash logs, diagnostics | Firebase Crashlytics | App stability | URLs and hosts are scrubbed from logs and exception messages by `LogScrubber`. |

## Collected for ads

| Data | SDK | Purpose | Notes |
|---|---|---|---|
| Advertising ID, device info, ad interactions | Google Mobile Ads, UMP | Advertising | Requested only after UMP consent allows it. Ads show only on native screens. |

## Network requests the app itself makes

| Request | When | Contains |
|---|---|---|
| Filter lists (EasyList, EasyPrivacy, IndianList) | Weekly, on unmetered networks only | Nothing about the user. https only. |
| Firebase Remote Config | After first frame, at most every 6 hours | Standard Firebase instance data. |

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Browsing, and the unmetered-only update constraint. |
| `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Only when a site asks and the user agrees for that site. |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Only when a site asks and the user agrees for that site. |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and lower) | Saving downloads to the public Downloads folder. |

Never requested: `QUERY_ALL_PACKAGES`, SMS, call log, accessibility.

## Open items before the first release

- Publish the privacy policy, then set `privacy_policy_url` in Remote Config.
- Confirm the Firebase Analytics default-consent settings against the current Play form wording.
