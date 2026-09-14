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

## Limitations

Pages stay plain HTTP, so they run in an insecure context: no push
notifications, geolocation, camera, Clipboard API, WebAuthn or `crypto.subtle`,
and traffic crosses the LAN unencrypted. If that is ever needed, the way out is a
self-signed certificate on the PC with its authority pinned in the WebView.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 37.2 and build-tools
37; installing `cmdline-tools` alone is enough, no Android Studio). Point
`sdk.dir` in `local.properties` at the SDK, or set `ANDROID_HOME`.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit tests, on the JVM, no device needed
```

## Installing on a phone

With wireless debugging (or `adb tcpip 5555` once over USB):

```
adb connect <phone-ip>:5555
adb devices                    # must say "device", not "unauthorized"
./gradlew installDebug
```

The first connection shows a prompt on the phone to allow debugging from this
computer.

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
