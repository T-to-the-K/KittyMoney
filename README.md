# Kitty Money

A simple Android app for tracking **rotating savings groups** (a "kitty" / susu / chama) — built in Kotlin on the native Android stack.

Made for family use: a lightweight, on-device way to keep records of who contributes and whose turn it is to collect the pot each cycle.

## Features

- **Kitty groups** — create a group with a name and a per-share contribution amount (or go free-form "any amount").
- **Shares** — members hold 1 share by default; a double slot = 2 shares (pays double, collects twice), and half-shares split a slot (0.5 each, pay half, collect half).
- **20-member model** — bulk-add members by pasting comma/newline-separated names to set up large committees fast.
- **Rotation** — the app schedules payouts across every share slot in round-robin order and shows who's next and what pot to expect.
- **Members** — add, bulk-add, or remove members, and change anyone's shares at any time.
- **Cycles** — start a new cycle; the app auto-assigns the payee(s) from the share rotation.
- **Payments** — tick each member off once they contribute to a cycle (they can give any amount).
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
