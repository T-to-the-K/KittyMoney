# Kitty Money

A simple Android app for tracking **rotating savings groups** (a "kitty" / susu / chama) — built in Kotlin on the native Android stack.

Made for family use: a lightweight, on-device way to keep records of who contributes and whose turn it is to collect the pot each cycle.

## Features

- **Kitty groups** — create a group with a name and a fixed per-member contribution amount.
- **Members** — add or remove members at any time.
- **Cycles** — start a new cycle; the app auto-assigns the payee in round-robin order.
- **Payments** — tick each member off once they contribute to a cycle.
- **Balance** — see how much has been collected and completed cycles.
- **Local persistence** — all records are saved on the device (JSON in app storage). No accounts, no cloud.

## Build

Requires Android SDK (compileSdk 35) and JDK 17.

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Test

```bash
./gradlew testDebugUnitTest
```

## Install on a phone

Sideload the release APK (or `app-debug.apk`) with "Install unknown apps" enabled for your file manager / browser.

## License

MIT
