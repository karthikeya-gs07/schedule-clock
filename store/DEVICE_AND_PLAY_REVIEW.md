# Calen-Time: device check and Play Store readiness (2026-10-31)

Phone app 0.9.1 (debug build), demo mode (sample day, no real calendar), Android 16 emulators plus the real Galaxy Z Fold7.

## 1. Devices tested

| Device | Screen (dp) | Today | Watchface | Calendars | Settings | About | Crashes |
|---|---|---|---|---|---|---|---|
| Galaxy Z Fold7, folded (real) | 411 × 960 | ✅ | ✅ | ✅ | ✅ | ✅ | none |
| Galaxy Z Fold7, unfolded (real) | 750 × 832 | ✅ two panes | ✅ grid | ✅ | ✅ | ✅ right pane | none |
| Small phone (emulator) | 360 × 640 | ⚠️ drawer shows 1 meeting | ⚠️ page dots overlap the watch | ✅ | ✅ | ✅ | none |
| Pixel 9 (emulator) | 411 × 923 | ✅ | ✅ | ✅ | ✅ | ✅ | none |
| Pixel 9, landscape | 923 × 411 | ❌ two panes on a short screen: watch cut off, drawer gone | ❌ cards behind the nav | ⚠️ | ⚠️ | — | none |
| Pixel 9 Pro Fold, folded (emulator) | 443 × 994 | ✅ | ✅ | ✅ | ✅ | ✅ | none |
| Pixel 9 Pro Fold, unfolded (emulator) | 851 × 882 | ✅ | ✅ | ✅ | ✅ | ✅ | none |
| Pixel Tablet, portrait (emulator) | 800 × 1280 | ✅ | ✅ | ✅ | ✅ | ✅ | none |
| Pixel Tablet, landscape (emulator) | 1280 × 800 | ❌ left pane cut: the drawer is below the screen | ✅ | ✅ | ✅ | ✅ | none |

No crashes on any device. Everything that breaks has one cause: **Today is laid out from the screen's width only** (the Figma frame scaled by width / 393), so on any short screen (landscape phone, landscape tablet, small phone) the watch and drawer run past the bottom. The two-pane switch also looks only at width (≥ 600 dp), so a landscape phone gets two cramped panes.

### Fixes

1. **Scale Today by whichever runs out first**: `s = min(width / 393, height / 852)`, centring the block horizontally. Short screens then show the whole layout, smaller.
2. **Two panes only when the screen is also tall enough**: `wide = width ≥ 600 dp && height ≥ 600 dp`. A landscape phone stays one pane.
3. **Phones stay portrait**: `android:screenOrientation="portrait"` on the activity. Android 16 ignores this on large screens (unfolded Fold, tablets), which keep rotating freely and use the two-pane layout, so it only affects phones, where the app is designed portrait-first. Play accepts this for phones.
4. **Small phones**: on Watchface, position the dots and name from the bottom block's height rather than a fixed 300 dp, so they never overlap the watch below ~700 dp tall.

Minor, demo mode only: with no watch connected (the emulators) the sync pill reads "Galaxy Watch8 ·" with nothing after the dot. Not seen by real users.

## 2. Play Store readiness

### Fine as it is

- **Target SDK 36** (Play requires 35 for new apps; 36 meets the next deadline too). Phone minSdk 29, watch 34.
- **16 KB page size**: the only native library (`libandroidx.graphics.path.so`) is 16 KB aligned (`zipalign -P 16` passes).
- **No internet permission** on either app; calendar data only goes phone → own watch over the Data Layer. Logs don't contain meeting titles or places.
- **Release builds**: R8 on, signed with the upload key, distinct version codes (phone 1002, watch 2002) under one package, all 16 faces pass Google's Watch Face Push validator at build time.
- **Store assets**: icon, feature graphic, 6 phone screenshots, 2 Wear OS screenshots, listing text and data safety answers are in `store/`.
- **Large screens**: the activity is resizable; the unfolded and tablet layouts exist (after the fixes above, they hold up in every orientation).

### Needs fixing or deciding before submitting

| # | Severity | Issue | Why Play cares | Fix |
|---|---|---|---|---|
| 1 | **High** | **Watch Face Push used to install our own face.** Google's docs say the push API is for watch face *marketplace* apps; Play review may reject an app that pushes only its own faces. | Policy fit; the most likely rejection reason. | Submit with a clear review note (the face needs its watch app's calendar data, so it can't be a standalone face listing; colourways are picked in the phone app). Fallback if rejected: publish the face itself as a WFF watch face listing and keep the watch app as its data source (complications). Decide before uploading. |
| 2 | **High** | **Privacy policy not online, no contact email.** `docs/privacy.html` exists but isn't published. | Required for any app asking for calendar access. | Publish `docs/` with GitHub Pages (or any URL), add a public contact email in Play Console. |
| 3 | **Medium** | **Debug deep links ship in the release watch app.** `TapActivity` is exported and accepts `/swap?colour=`, `/day?name=` (switch to a sample day) and `/offset=` (shift the clock). Any app on the watch could change the face or put it on fake meetings. | Security/quality review; an exported entry point that changes data. | Only handle `/swap`, `/day` and `/offset` when `BuildConfig.DEBUG`; release keeps `/select`, `/release` and `/colour`. |
| 4 | **Medium** | **Demo mode can be switched on by any app** (`--ez demo true` on the exported launcher activity), and it saves "setup done". | Same as above, smaller impact. | Honour the `demo` extra only in debug builds, and don't persist setup from it. |
| 5 | **Medium** | **Watch minSdk 34 (Wear OS 5) but the face needs Wear OS 6.** Wear OS 5 users can install it and only get "Your watch needs an update". | Bad reviews; the listing says Wear OS 6. | Set the watch app's `minSdk = 36`, so Play only offers it to Wear OS 6 watches. |
| 6 | **Medium** | **`USE_EXACT_ALARM`** (meeting buzzes). Play restricts it to alarm and calendar apps and asks for a declaration. | Permission declaration form at upload. | Declare it as a calendar app (text drafted in `LISTING.md`). If refused, switch to `SCHEDULE_EXACT_ALARM` with a fallback when not granted. |
| 7 | **Medium** | **Landscape and short screens** (section 1). | Play's large-screen quality checks and "app breaks in landscape" reports. | Fixes 1–4 in section 1. |
| 8 | Low | The watch app says it's standalone (`com.google.android.wearable.standalone = true`). | Standalone apps must be useful without the phone. | Fine if the watch reading its own calendar is the intended fallback; otherwise set it to false so Play requires the phone app. |
| 9 | Low | No tablet screenshots. | Optional, but the tablet listing looks empty without them. | Take 2 screenshots on the tablet emulator after the layout fixes. |

### Still on your side (unchanged)

Play developer account, contact email, publishing the privacy policy, rights to the watch photo used in the app and screenshots, and a backup of `~/.android/calen-time/` (signing keys).

## 3. Tools added for this check

Google's Android command-line tools (`~/Library/Android/sdk/cmdline-tools/latest`) and one Android 16 phone emulator image (`system-images;android-36.1;google_apis;arm64-v8a`), about 1.8 GB, plus four emulators: `CT_Small`, `CT_Pixel9`, `CT_Fold`, `CT_Tablet`. They can be deleted from Android Studio's Device Manager when no longer needed.
