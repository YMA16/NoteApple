# NoteApple for Android

An original Android notes app with a familiar, minimal notes workflow, dark mode, accent themes, local storage, file export/import, and Google Drive app-data backup.

## Build

Open the project in Android Studio or run `gradle assembleDebug` with Android SDK 35 installed.

The GitHub Actions workflow builds a debug APK on pushes to `main` and uploads it as a workflow artifact.

## Google Drive backup setup

Enable the Google Drive API in a Google Cloud project and configure an Android OAuth client for the application id `com.yma.noteapple` using the signing certificate fingerprint. The app requests the Drive `appDataFolder` scope at runtime; backups are private to the app and are not listed in the user's regular Drive files.

## Privacy

Notes are stored locally on-device. Drive backup runs only when explicitly requested by the user. Export files are created only after the user chooses a destination.
