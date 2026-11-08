# AdMob and app store setup

Researched November 2026. Store rules change often: check each store's own pages before you start.

## Where to publish, in order

| Store | Cost | Open to an individual in India? | Counts for AdMob? | Verdict |
|---|---|---|---|---|
| Google Play | US $25 once | Yes | Yes | **Main store.** Start here. |
| Indus Appstore (PhonePe) | Was free until April 2025; now a small yearly fee (check) | Yes | Not on AdMob's list | **Second store**, for Indian reach. Comes pre-installed on new Xiaomi, Redmi and Poco phones in India. |
| Samsung Galaxy Store | Free | Only as a registered business (needs a D-U-N-S number) | Yes | Later, once you register a business. |
| OPPO App Market | Free account | Unclear for individuals | Yes | Ask OPPO support before trying. |
| vivo App Store | Unknown | Unclear | Yes | Ask vivo support before trying. |
| Xiaomi GetApps | Free | Unclear | Yes | Skip: replaced by Indus on Indian Xiaomi phones since January 2025. |
| Amazon Appstore | — | — | — | Skip: closed on non-Amazon Android phones since 20 August 2025. |
| F-Droid | Free | Yes | — | Not possible: it does not accept apps with ad SDKs. |

**How AdMob fits in:** AdMob gives full ads only after the app is linked to a listing in a store it supports (Google
Play, Samsung, OPPO, vivo, Xiaomi, Amazon). Until then it serves only a trickle of ads. The ads go to the app, not to
one store, so once AdMob has reviewed your Play listing, copies installed from Indus or your website show ads too, as
long as they use the same package name.

---

## Part 1: AdMob

### 1. Account

1. Go to admob.google.com and sign in with the Google account you will publish with.
2. Country: India. Time zone: India. Currency: INR.
3. Payments > Payment profile: your legal name and address. Add PAN under tax information. Bank details can wait
   until you reach the payout threshold.

### 2. Add Theta

1. Apps > Add app > Android.
2. "Is the app listed on a supported app store?" Choose **No** for now (link it after Play listing, step 6).
3. Name: Theta. Turn off user metrics if you do not want Firebase linking yet.
4. Copy the **App ID** (`ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`).

### 3. Create four ad units, all "Native advanced"

Native advanced is **not** a banner. A banner is a fixed rectangle drawn by Google. A native ad is delivered as parts
(headline, icon, picture, button) that Theta lays out in its own style, which is why it looks like part of the app.

| Ad unit name | Screen | Goes in `local.properties` as |
|---|---|---|
| Theta - New tab | New-tab page | `ADMOB_NATIVE_NEW_TAB_ID` |
| Theta - Downloads | Downloads list | `ADMOB_NATIVE_DOWNLOADS_ID` |
| Theta - Bookmarks | Bookmarks list | `ADMOB_NATIVE_BOOKMARKS_ID` |
| Theta - History | History list | `ADMOB_NATIVE_HISTORY_ID` |

For each: Ad units > Add ad unit > **Native advanced** > name it > Create. Leave the options at their defaults.

**Theta does not use interstitial (full-screen) ads.** If you already created an interstitial unit, leave it or delete
it; the app never loads it. Full-screen ads stay off until the VPN feature.

### 4. Put the IDs in the project

Open `local.properties` in Android Studio and add the five lines from `docs/RELEASING.md` step 5. The file is already
ignored by git, so the IDs are never committed.

### 5. Privacy & messaging (required by Google)

1. Privacy & messaging > **European regulations** > Create message > select Theta > publish. Shown only in the
   EEA, the UK and Switzerland.
2. Privacy & messaging > **US state regulations** > Create message > select Theta > publish.
3. Theta already shows these through Google's consent SDK, after its own privacy notice, and offers a "Privacy
   choices" row in settings where the rules require it.

### 6. Blocking controls (recommended for India)

Blocking controls > Sensitive categories: block **Gambling and betting** (illegal in several Indian states), and
consider Dating, Get-rich-quick and Sexual content. Ad review centre: check now and then for anything you do not want.

### 7. After the Play listing is live

1. Apps > Theta > App settings > **Add store** > Google Play > search for Theta > link.
2. AdMob reviews the app (usually a few days). Full ads start after approval.
3. **app-ads.txt:** AdMob shows a line like
   `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`.
   Put it in a file named `app-ads.txt` at the top of your website (`https://yourdomain/app-ads.txt`), and use the
   same website as the "Website" field of your Play listing. Without it, far fewer ads are shown.

### 8. Never

- Never tap your own real ads, or ask others to. It is the most common reason accounts are banned.
- Add your phone under Settings > Test devices, and use debug builds for testing (they always show test ads).

---

## Part 2: Google Play

### 1. Developer account

1. play.google.com/console > create a **personal** account (US $25, one time).
2. Verify your identity with a government ID, and your phone number and email.
3. Note: personal accounts created after November 2023 must run a **closed test with at least 12 testers for 14 days
   in a row** before the app can go public. Start gathering testers (friends, family, an online tester group) early.

### 2. Create the app

All apps > Create app: name **Theta**, default language English (India), App, Free. Accept the declarations.

### 3. Store listing (Grow > Store presence > Main store listing)

- **Short description** (80 characters), for example: "Fast, private browser with ad blocking, made for every
  Indian phone."
- **Full description** (up to 4000): features, privacy, languages. Do not name other browsers or apps.
- **App icon:** 512 x 512 PNG.
- **Feature graphic:** 1024 x 500.
- **Phone screenshots:** at least 2, 1080 x 1920 or similar. Show the new-tab page, tab switcher, settings,
  private tab.
- **Translations:** add Hindi and Marathi listings (Store presence > Translations) for Indian reach.
- **Category:** Communication (where browsers are listed). Add contact email and **website** (needed for
  app-ads.txt).

### 4. App content (Policy > App content), every section must be complete

- **Privacy policy:** a public URL. Required, because Theta shows ads. Host it on your website.
- **Ads:** Yes, contains ads.
- **App access:** all features available without login.
- **Content rating:** fill in the questionnaire. Choose "browser" where asked; a browser can reach any content.
- **Target audience:** 18 and over is the simplest for a browser with ads (13+ is possible but adds rules).
- **Data safety:** fill it in from `docs/data-safety.md`.
- **Government apps, financial features, health:** No.

### 5. Testing, then production

1. Testing > **Internal testing**: upload the signed `.aab`, add your own email, install and check.
2. Testing > **Closed testing**: create a track, add at least 12 testers' emails, upload the same build, share the
   opt-in link. Keep them opted in for 14 days.
3. After 14 days: Dashboard > **Apply for production access**, answer the questions about your test.
4. Production > Create release > upload > release to 20 % > raise to 100 % once stable.

### 6. Then link AdMob (Part 1, step 7)

---

## Part 3: Indus Appstore

1. Go to the Indus Appstore developer platform (indusappstore.com, Developers) and sign up with your phone number.
2. Complete the identity check it asks for (expect PAN, possibly Aadhaar, for an individual). Check the current
   yearly listing fee before paying.
3. Add app: upload the **signed APK** (Indus also accepts .aab), same package name as Play.
4. Listing: the same text and screenshots as Play. Indus supports 12 Indian languages, so add Hindi and Marathi.
5. Privacy policy URL and content rating, as on Play.
6. Submit for review.

Ads in copies installed from Indus work once AdMob has approved the Play-linked app.

---

## Part 4: Samsung Galaxy Store (when you have a registered business)

1. Register a business (for example a sole proprietorship) and request a D-U-N-S number for it from Dun &
   Bradstreet India. Samsung's support has said a registered sole proprietorship can qualify; confirm with them.
2. seller.samsungapps.com > sign in with a Samsung account whose country is India > request **Commercial seller**
   status with the business details and D-U-N-S number.
3. Add new application > Android > upload the signed APK > same listing text > submit.
4. Then add the Galaxy Store listing in AdMob as well.

## Part 5: OPPO and vivo

Both are free to join and supported by AdMob, but neither publishes clear rules for individual developers in India.
Before investing time, contact their developer support (OPPO: developers.oppomobile.com; vivo: its developer
portal) and ask whether an individual from India can publish, and which documents they need.

---

## Every update, in every store

- Same package name, same signing key, higher `versionCode` (see `docs/RELEASING.md`).
- Upload to Google Play first (staged rollout), then Indus and the others once Play shows no crash spike.
- Paste the same "What's new" text from the changelog everywhere.
