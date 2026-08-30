# Kitty Money

A simple Android app for tracking **rotating savings groups** (a "kitty" / susu / chama) — built in Kotlin on the native Android stack.

Made for family use: a lightweight, on-device way to keep records of who contributes and whose turn it is to collect the pot each cycle.

## Features

- **Kitty groups** — create a group with a name and a **target threshold** (the total your kitty must reach), or go free-form "any amount".
- **Shares** — members hold 1 share by default; a double slot = 2 shares (pays double, collects twice), and half-shares split a slot (0.5 each, pay half, collect half).
- **20-member model** — bulk-add members by pasting comma/newline-separated names to set up large committees fast.
- **Rotation** — the app schedules payouts across every share slot in round-robin order and shows who's next and what pot to expect.
- **Monthly timeline** — a kitty runs for as many months as it has payout slots (total shares), or for the run-length you set. The member(s) collecting the current month get a highlighted row.
- **Fixed payments from the threshold** — with a target set, each member's monthly payment is fixed by their share: full share pays (target ÷ months ÷ total shares), half pays half. The app **rejects** any amount that doesn't match, so the books always land exactly on the target.
- **Members** — add, bulk-add, or remove members, and change anyone's shares at any time.
- **Months** — start one month (cycle) at a time; the app auto-assigns the payee(s) from the share rotation and labels each month "Month N of M".
- **Adopt a running kitty** — already mid-way through a kitty that started before the app? Tell the app how many months are already done: they're marked "done · before app" and the rotation continues from the current month.
- **Spreadsheet board** — one table for all running kitties: rows are members, a column per kitty, with collected totals, targets, and what's left.
- **Payments** — tick each member off once they contribute to a month (fixed amount for threshold kitties, or any amount in free-form mode).
- **Balance** — see how much has been collected and completed months.
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
