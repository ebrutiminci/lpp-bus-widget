# LPP Bus Widget

An Android home-screen widget that shows when the next bus comes to the stops you use, in Ljubljana (LPP).

- Set it up once: search your stop, then tick the bus you take, written like the sign on the bus (for example **3 → RUDNIK**). The app works out which side of the road you need.
- See up to two stops side by side, with arrival times such as **09:52**. A `*` means a timetable time instead of live GPS.
- Tap the widget to refresh it. When a bus has left, the next one moves up by itself.
- Refreshes itself about every 15 minutes during the hours you usually look (learned from your taps, on the phone only).
- The app tells you when a new version is available.

## Install

On your Android phone, open:

**https://github.com/ebrutiminci/lpp-bus-widget/releases/latest/download/lpp-bus.apk**

Tap **Install**. If Android asks, allow installing apps from your browser.
Then long-press your home screen → **Widgets** → **LPP Bus** → drag it onto the screen, and open the **LPP Bus** app to choose your stops.

To update, open the same link again and tap **Update**. Your stops are kept.

## How releases work

Every push to `main` runs [`.github/workflows/build.yml`](.github/workflows/build.yml), which builds a signed APK and publishes it as a new release. Nobody needs Android Studio.

The signing key is stored only in the repository's GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), with an offline backup kept by the owner. It is never committed. All releases have to be signed with the same key, or phones refuse to install the update.

## Building it yourself

Open the folder in Android Studio and use **Build → Build APK(s)**. A debug build is signed with your own computer's key, so it can't update the version from GitHub. Uninstall that version first if you switch.

## Data

Bus data comes from the LPP open data API (https://data.lpp.si). This project isn't made by or affiliated with LPP.

## License

GPL-3.0. See [LICENSE](LICENSE).
