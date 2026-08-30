# Kitty Money

A simple Android app for tracking **rotating savings groups** (a "kitty" / susu / chama) — built in Kotlin on the native Android stack.

Made for family use: a lightweight, on-device way to keep records of who contributes and whose turn it is to collect the pot each cycle.

## Features

- **Kitty groups** — create a group with a name and a **monthly pot** (the amount handed to a full-share collector each month), or leave it blank for free-form.
- **Shares** — members hold 1 share by default; a double slot = 2 shares (pays double, collects twice), and half-shares split a slot (0.5 each, pay half, collect half).
- **20-member model** — bulk-add members by pasting comma/newline-separated names to set up large committees fast.
- **Rotation** — the app schedules payouts across every share slot in round-robin order and highlights next month's collector(s): a full share is one person receiving the pot, two half shares are highlighted together each receiving half of it.
- **Monthly timeline** — a kitty runs for as many months as it has payout slots (total shares), or for the run-length you set. The member(s) collecting the current month get a highlighted row.
- **Manager records payments** — the app never pre-sets or rejects amounts; the manager types what each person actually gave (full, half, or anything). The pot is only used to tell everyone what the payout will be.
- **Members** — add, bulk-add, or remove members, and change anyone's shares at any time.
- **Months** — start one month (cycle) at a time; the app auto-assigns the payee(s) from the share rotation and labels each month "Month N of M".
- **Adopt a running kitty** — already mid-way through a kitty that started before the app? Tell the app how many months are already done: they're marked "done · before app" and the rotation continues from the current month.
- **Spreadsheet board** — one table for all running kitties: rows are members, a column per kitty, with collected totals and the monthly pot for each.
- **Payments** — the manager ticks each member off with the amount they actually gave for that month.
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
