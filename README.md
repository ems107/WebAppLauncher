# WebAppLauncher

An Android launcher for personal web apps served from a PC on the local
network. Each configured page opens full screen in a WebView, with no address
bar, and can be pinned to the home screen so it looks and behaves like an app
of its own.

It exists because Chrome will not install a PWA from a plain `http://` LAN
address, which is never a secure origin. A native launcher does not need
Chrome's permission: it supplies the icon, the home-screen entry and the
window itself.

## What it does

- **Pages with several addresses.** A page lists the URLs its server may answer
  on, for instance a PC's cable and Wi-Fi addresses. Opening it tries the one
  that answered last time; if that fails, all of them are probed at once and the
  first to give any HTTP answer wins (a 401 counts: the server is there). Moving
  the PC from cable to Wi-Fi needs no change.
- **An honest error.** When nothing answers, the screen lists every address and
  what it said (no answer in time, refused, unreachable), with a retry.
- **Full screen, with state kept.** JavaScript, DOM storage and cookies are on,
  so a site's login survives closing the app. Links to other sites open in the
  browser; back walks the page's history and then leaves; pulling down from the
  top reloads.
- **Home-screen shortcuts.** A page's menu pins it to the home screen, and the
  launcher icon's long-press menu lists the pages. Each page runs as its own
  task, with its name and icon in recents.
- **Icons from the site.** The first time a page opens, its icon is taken from
  the web app manifest (maskable icons preferred), `apple-touch-icon`,
  `<link rel="icon">` or `/favicon.ico`, in that order. SVG icons are skipped.
  Without any, the page gets a tile with its initial. The editor can also use a
  picked image.
- **Export and import.** The list's menu saves the configuration to a JSON file
  and loads it back. Icons are not included; they are fetched again.
- **Updates itself from GitHub.** Once an hour, and when the page list is opened
  after more than that, the app looks for a newer
  [release](https://github.com/ems107/WebAppLauncher/releases). A card at the
  top of the list says so until it is installed: tapping it shows the release
  notes, downloads the APK and hands it to Android, which asks for confirmation.
  The first time, Android also asks to allow this app to install apps. "Check
  for updates" in the list's menu asks right away. An open page never shows any
  of it, and debug builds do not look for updates at all.

## Limitations

Pages stay plain HTTP, so they run in an insecure context: no push
notifications, geolocation, camera, Clipboard API, WebAuthn or `crypto.subtle`,
and traffic crosses the LAN unencrypted. If that is ever needed, the way out is a
self-signed certificate on the PC with its authority pinned in the WebView.

## Building

Requirements: JDK 17 or newer and the Android SDK with platform 37.2 (installing
`cmdline-tools` alone is enough, no Android Studio). Point
`sdk.dir` in `local.properties` at the SDK, or set `ANDROID_HOME`.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit tests, on the JVM, no device needed
```

## Installing on a phone

The simplest way is the APK of the latest
[release](https://github.com/ems107/WebAppLauncher/releases); from then on the
app offers every new one.

Play Protect may step in after Android's "Install" to recommend scanning a
version it has not seen. If the card then says "Installation cancelled", Retry:
the second attempt installs without asking again.

For development, with wireless debugging (or `adb tcpip 5555` once over USB):

```
adb connect <phone-ip>:5555
adb devices                    # must say "device", not "unauthorized"
./gradlew installDebug
```

The first connection shows a prompt on the phone to allow debugging from this
computer.

A debug build is signed with a different key from the releases, so Android will
not install one over the other: uninstall first, after exporting the
configuration.

## Publishing a release

```
.\scripts\release.ps1 -Version X.Y.Z -NotesFile notes.md    # or -Notes "..."; -DryRun to only build
```

The script checks the repository is clean and on `main`, writes the version to
`gradle.properties`, runs the tests, builds the release APK, verifies it carries
the release key, commits, tags (the tag message is the release notes, which the
app shows before installing), pushes and publishes the GitHub release with `gh`.

Releases are signed with the key in `%USERPROFILE%\.android\weblauncher-release.jks`,
described by `weblauncher-release.properties` next to it. **Keep a copy of both
somewhere safe**: an update signed with any other key is refused by every phone
that has the app, and the only way back is uninstalling it.

## Configuration file

Pages are kept in one JSON file in the app's private storage; the export is the
same format:

```json
{
  "version": 1,
  "pages": [
    {
      "id": "0b0c6f1e-…",
      "name": "Jackery",
      "urls": ["http://192.168.1.10:8731", "http://192.168.1.11:8731"],
      "iconPath": null
    }
  ]
}
```

The `id` is what home-screen shortcuts point at, so it survives renames and an
export/import round trip.
