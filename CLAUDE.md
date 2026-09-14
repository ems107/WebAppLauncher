# WebAppLauncher

An Android app that launches personal web apps served from a PC on the local
network: a list of configured pages, each opening full screen in a WebView,
each with its own shortcut on the home screen.

**Work in progress on the `web-launcher` branch.** The agreed plan and its
current status live in `web-launcher-progress.md`. Read it before writing code --
it carries the phases, the verification steps and the decisions already settled.

## Why this exists

Edgar runs personal web apps on his own PC and reaches them from his phone over
the LAN. The first is the Jackery Explorer 240 interface (repository
`../JackeryExplorer240`, served by its daemon on port 8731); more are coming.

Reaching them from the phone works, but it is not comfortable: a browser tab
with an address bar, and no way to install them. Chrome will not offer to
install a PWA unless the origin is secure, and `http://` on a LAN address never
is -- `localhost` counts, a private IP does not.

**The insight this project is built on:** if the installed app is a native app
of our own, Chrome's installability criteria stop mattering. The launcher
supplies the icon, the home-screen entry and the window without an address bar.
Being a PWA was never the goal; it was one route to it.

## Decisions already taken, and what they cost

**The certificate route was considered and rejected.** A real domain, a wildcard
certificate from Let's Encrypt over a DNS-01 challenge, and Caddy as a reverse
proxy would give a secure origin and genuine PWA installs. It works, it is the
standard homelab answer, and it was turned down deliberately: it introduces
infrastructure, renewals and concepts the owner does not want to maintain for
what he actually wants, which is to open his apps comfortably from his phone.
Do not reintroduce it without being asked.

**The consequence, accepted knowingly:** traffic stays plain HTTP, so the pages
run in an insecure context. No push notifications, geolocation, camera,
Clipboard API, WebAuthn or `crypto.subtle`, and credentials cross the LAN in the
clear. No current app needs any of that.

**The way out, if it is ever needed:** a WebView can pin its own certificate
authority, so a self-signed certificate on the PC would give a real `https://`
origin with nothing installed on Android and no warning banner. That is roughly
twenty lines in the launcher plus a certificate on the PC, and it does not
require rewriting anything. It is the upgrade path, not part of the first
version.

**Several URLs per page, raced in parallel.** The PC answers on two different
addresses depending on whether it is on ethernet or Wi-Fi. Rather than solving
that in DNS, each page carries an ordered list of URLs and the launcher probes
them all at once, keeping the first that answers. Any HTTP response counts as
alive -- a 401 proves the server is there, which is the only question being
asked. This is a core feature, not a nicety: it is what makes switching between
cable and Wi-Fi invisible.

## Environment on this machine

- **JDK 21** is at `C:\Program Files\Android\openjdk\jdk-21.0.8` (left by another
  installer). `JAVA_HOME` is not set system-wide: set it in the shell before
  running Gradle.
- **The Android SDK** lives in `%LOCALAPPDATA%\Android\Sdk`, installed from
  `cmdline-tools` only -- Android Studio is deliberately not used.
  `local.properties` (ignored) points `sdk.dir` at it. `sdkmanager` now just
  forwards to the new Android CLI: install packages with
  `cmdline-tools\latest\bin\android.exe --no-metrics sdk install "<package>"`.
  It exits with code 9 even when the install succeeded; check the folder.
- **Building happens from the terminal** with the Gradle wrapper, not from an
  IDE. There is no emulator: every visual check happens on the real phone.
- **The phone is reached with `adb` over the network**, not USB:
  `adb connect <phone-ip>:5555`, and `adb devices` must say `device` (not
  `unauthorized`) before anything else is worth trying. Edgar gives the address
  and unlock PIN; neither is written into the repository. Visual checks are
  `adb exec-out screencap -p` into the scratchpad, never into the repo.
- **The test phone is a Urovo DT50**: Android 9 (API 28), 720x1440, with the
  stock Launcher3 as home app (it supports pinned shortcuts). Its WebView
  provider is Chrome (138 when checked), not the system WebView package, which
  is stuck at 74 and disabled -- so modern JavaScript works. If pages ever break
  on syntax, check `adb shell dumpsys webviewupdate` before blaming the page.
  A cold start takes about four seconds: wait before taking a screenshot.
- **Driving the UI from adb**: `uiautomator dump` fails ("null root node") on
  Compose screens with a focused text field or a dialog, so read tap
  coordinates off a screenshot instead -- screenshot pixels are screen pixels.
  `adb shell run-as es.edgarms.weblauncher cat files/config.json` shows what was
  actually saved (debug builds only).

## Conventions

- **Language.** Code, comments, commits, documentation and this file: English.
  Anything written for Edgar to read -- answers, questions, implementation
  plans, including `web-launcher-progress.md` -- Spanish.
- **Kotlin with Jetpack Compose** (Material 3), package `es.edgarms.weblauncher`,
  `minSdk 26` (pinned shortcuts need it), `targetSdk 36`. `compileSdk` is 37.2
  because current AndroidX and OkHttp refuse anything older; that does not
  change runtime behaviour. Versions are pinned in `gradle/libs.versions.toml`
  (AGP 9, whose built-in Kotlin replaces the `kotlin-android` plugin).
- **Configuration is one JSON file** in `filesDir`, via `kotlinx.serialization`.
  No Room, no DataStore: the list is tiny, and the file being the format makes
  export and import free.
- **Tests run on the JVM**, with no device. The URL race, the JSON round trip
  and the icon fallback chain are all testable that way, and should stay that
  way -- inject a fake prober rather than reaching for the network.
- Branch and merge discipline comes from `../CLAUDE.local.md`. This is a written
  plan, so it gets its own branch (`web-launcher`) off `main`, progressive
  commits with the progress file updated alongside, and a `--no-ff` merge only
  once Edgar has tried it and said so.

## Traps that will cost an afternoon

- `domStorageEnabled` is **off** by default in a WebView. The app loads, looks
  fine, and silently loses all state.
- Cleartext HTTP is blocked by default since Android 9. Without
  `usesCleartextTraffic` in the manifest nothing loads at all.
- Cookies need `CookieManager.setAcceptCookie(true)` and a `flush()` when the
  activity pauses, or the session is gone on every exit -- which for the Jackery
  app means retyping the PIN constantly.
- Not every launcher supports pinning shortcuts. Check
  `isRequestPinShortcutSupported` and say so rather than failing silently.
