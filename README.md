# NoteApple for Android

An original Android notes app with a familiar, minimal notes workflow. The app is built with Kotlin and Jetpack Compose and stores notes locally in SQLite.

## Features

- Hebrew RTL interface, note list, search, note editor, pinning and folders.
- System/light/dark appearance and six accent palettes.
- Export all notes as JSON, TXT, or ZIP (containing JSON and TXT).
- Import a JSON backup.
- Google Drive backup and restore using the private `appDataFolder` area and Google's runtime authorization flow.
- Custom dark notebook launcher icon.

## Build

Open the project in Android Studio with Android SDK 35, or run `gradle assembleDebug` with Gradle 8.9 and JDK 17. The GitHub Actions workflow builds a debug APK on pushes to `main` and uploads it as the `NoteApple-debug-apk` workflow artifact.

## Google Drive setup

To use Drive backup, enable the Google Drive API in a Google Cloud project and configure an OAuth Android client for application id `com.yma.noteapple` using the SHA-1 fingerprint of the APK signing certificate. The app requests `https://www.googleapis.com/auth/drive.appdata` only when the user taps a Drive backup or restore action. A Drive backup is private to this app and is not listed alongside ordinary Drive files.

For the APK built by GitHub Actions, its signing certificate fingerprint can be displayed with `apksigner verify --print-certs app-debug.apk`. Configure that fingerprint in the Google Cloud Android OAuth client before using Drive with that build. A locally built debug APK uses the local Android debug keystore and therefore has a different fingerprint.

## Privacy

Notes remain on the device unless the user explicitly exports them or runs a Google Drive backup. Exports are written only after the user selects a destination.
