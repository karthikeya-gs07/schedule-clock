# CalenTime: Play Store listing (draft)

Everything Play Console asks for, ready to paste. Phase 4 of PRD §6.5. Items marked **You** need your account or a decision.

## App details

| Field | Value |
|---|---|
| App name (30) | CalenTime |
| Package | `io.github.karthikeyags07.scheduleclock` (phone and watch apps share it) |
| Category | Productivity |
| Tags | Calendar, Watch faces, Productivity |
| Contact email | **You** (shown publicly on the listing) |
| Privacy policy URL | **You**: publish `docs/privacy.html` (see below) |

### Short description (80 characters max)

> Meetings as arcs on your Wear OS watch face. Your next 12 hours at a glance.

(76 characters.)

### Full description (4000 max)

> **Your day, around the clock.**
>
> CalenTime turns your Wear OS watch face into a picture of your day. Every meeting is an arc on the ring, so a glance at your wrist shows how busy the next 12 hours are, what's next, and how much free time you have before it.
>
> **On your watch**
> • Meetings as arcs on a 12-hour ring, with the time in the middle
> • The next meeting's title, time and place underneath
> • Tap an hour on the ring to see what's on then
> • Double bookings and private meetings shown at a glance
> • Meetings later today wait on a thin inner ring until there's room
> • Gentle buzzes 2 minutes before a meeting, halfway through, and 5 minutes before it ends
>
> **Five looks**
> Amber, Mint, Violet and Rose, or Solaris, where the ring takes the colour of the light through your day: dawn rose, noon gold, sunset coral, night indigo.
>
> **On your phone**
> • Choose which calendars go to your watch: Google, Samsung, Outlook and Teams (with Outlook's calendar sync on)
> • Pick the watch face's colours and see it live before you choose
> • Turn each buzz on or off, and choose whether they come through Do Not Disturb
> • Setup puts the watch face on your watch for you
>
> **Private by design**
> Your calendar goes only from your phone to your own watch. CalenTime has no internet access, no accounts, no ads and no tracking. Private events reach the watch without their title or place.
>
> **Needs**
> An Android phone (Android 10 or newer) and a Wear OS 6 or newer watch, such as the Galaxy Watch8 or a Galaxy Watch updated to One UI 8 Watch.

## Graphics

| Asset | Spec | File |
|---|---|---|
| App icon | 512 × 512 PNG | `store/icon-512.png` (from `store/make_assets.py`) |
| Feature graphic | 1024 × 500 PNG | `store/feature-1024x500.png` |
| Phone screenshots | 2–8, 9:16, sides 320–3840 px, long side at most 2× short | **To take** in demo mode (sample data, never your calendar): `adb shell am start -S -n io.github.karthikeyags07.scheduleclock/.phone.MainActivity --ez demo true`. Today, the status card, the drawer, Watchface (Solaris), Calendars, Settings. The Fold's cover screen is too tall (2520 × 1080 is over 2:1); pad the sides to 1260 × 2520 or use the inner screen. |
| Wear OS screenshots | 1+, square, at least 384 × 384, no device frame, face only | ✅ `store/screenshots/wear-1-amber.png`, `wear-2-solaris.png` (454 × 454, busy sample day at 10:21 AM, from the Wear OS 6 emulator) |

## Data safety form (draft answers)

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share user data? | **No** | Google's definition counts data sent off the device to the developer or third parties. Calendar data only goes between the user's own devices via Google Play services' Wear OS connection; the developer never receives it. |
| Is data encrypted in transit? | Yes | The Wear OS Data Layer is encrypted. |
| Can users request deletion? | Not applicable (nothing collected); uninstalling removes everything | |
| Permissions declared | `READ_CALENDAR` (phone, watch), `USE_EXACT_ALARM`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`, `PUSH_WATCH_FACES`, `SET_PUSHED_WATCH_FACE_AS_ACTIVE` (watch) | |

**You:** confirm the "No" answer when filling the form; Play's help explains on-device and device-to-device transfers.

### Sensitive permissions

- **`USE_EXACT_ALARM`** (watch): Play only allows it for alarm and calendar apps. Declaration: "CalenTime is a calendar watch face; it buzzes at exact times around each meeting (2 min before start, halfway, 5 min before end)."
- **Watch Face Push**: Google's docs say the push API is meant for apps that run their own watch face marketplace, and that faces published on Play itself should use Watch Face Studio/Designer. CalenTime pushes only its own face. Play review may ask about this; the answer is that the face needs its watch app for calendar data, so it can't be a standalone face listing.

## Content rating

Questionnaire: utility/productivity app, no user-generated content, no sharing, no violence, etc. Expected rating: **Everyone / PEGI 3**.

## Target audience

18+ (simplest; the app isn't for children).

## Release

| Item | Value |
|---|---|
| Testing track first | **Internal testing** (up to 100 testers by email) |
| Phone bundle | `android/phone/build/outputs/bundle/release/phone-release.aab` (version 0.9.1, code 1002) |
| Wear OS bundle | `android/datasource/build/outputs/bundle/release/datasource-release.aab` (version 0.9.1, code 2002), uploaded under the **Wear OS** form factor of the same app |
| Signing | Play App Signing on. Upload key: `~/.android/calen-time/upload.jks` (**back it up**). Watch faces are signed separately with `~/.android/calen-time/faces.jks`. |
| Release notes | "First test release: meetings on your watch face, five colourways, calendar choice and meeting buzzes." |

## Steps in Play Console (you)

1. Create a developer account at play.google.com/console (one-time $25; identity check can take a few days).
2. **Create app** → name CalenTime, app, free.
3. **Setup → Advanced settings → Form factors**: add **Wear OS**.
4. Fill the store listing, data safety, content rating, target audience and privacy policy URL from this page.
5. **Testing → Internal testing** → create release → upload the phone `.aab`; then the Wear OS track → upload the watch `.aab`.
6. Add testers' emails, roll out, and share the opt-in link.
