# NoteApple Android

An independent native Android note-taking app inspired by the visible workflow and feature set of “Notes - iOS Style Notepad” by Baday Production. It is not the original application and does not use its private source code or assets.

## Current build

- Kotlin + Jetpack Compose, Hebrew / RTL, dark-first interface.
- iOS-like folder and note lists, search, pinning, sorting, subfolders, tags, and Recently Deleted (30 days).
- Auto-saving note editor with basic rich-text formatting, highlights, checklist items, page backgrounds, and a simple expression calculator.
- Image/document attachments, opening attachments through Android apps, audio recording, and Android speech-recognition insertion.
- Per-note password protection using PBKDF2 + AES-GCM; optional app passcode lock.
- JSON v3 backup with folders, note metadata, checklists, tags, lock state, and attachment bytes embedded as Base64. The importer also understands the first NoteApple JSON v1 format.
- ZIP backup containing the full JSON backup plus TXT, Markdown, HTML exports. Individual TXT, Markdown, HTML, and PDF exports are also available.
- Google Drive manual backup/restore in the private appDataFolder.
- Home-screen quick-note widget.
- System / light / dark modes and six accent colours.

## Build

Open in Android Studio with JDK 17 and Android SDK 35. Or run:

```bash
gradle assembleDebug
```

GitHub Actions builds a debug APK on each push to `main` and uploads it as the `NoteApple-debug-apk` artifact.

## Google Drive

Enable the Google Drive API in a Google Cloud project. Create an Android OAuth client for package `com.yma.noteapple` using the SHA-1 fingerprint of the signing certificate for the APK you install. For an APK built by GitHub Actions, inspect the APK certificate with `apksigner verify --print-certs app-debug.apk` and use that fingerprint. A locally built debug APK has a different signing certificate.

The current UI exposes manual backup and restore. It does not run scheduled background Drive backups yet.

## Backup format

JSON v3 is an object with `format`, `schemaVersion`, `exportedAt`, `folders`, and `notes`. Attachments include `dataBase64` so that a JSON export is portable between devices. Import offers merge or replace. The v1 importer accepts the previous `format: noteapple-backup` structure and legacy note fields `body` / `folder`.

## Notes / limitations

This is an independent implementation, not a byte-for-byte copy. The current build provides the functions listed above; advanced features such as automatic Drive scheduling, iCloud synchronization, full document-edge scanning, complete table editing, custom-font import, and biometric app unlock are not implemented yet. Android's speech-recognition provider determines whether transcription works offline on a device.

Notes are stored on the device unless the user explicitly exports them or uses Drive backup.