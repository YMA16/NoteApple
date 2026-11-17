# NoteApple — Hebrew localization patch

This repository now tracks the NoteApple localization patch for the third-party Android app **Notes - iOS Style Notepad 2.2.4** (Baday Production).

The earlier experimental Kotlin/Compose NoteApple project has been removed from the current branch. This project is a resource-level patch of the supplied, compiled Flutter XAPK; it is not a replacement implementation or the original app source.

## Delivered build

- File: `NoteApple.xapk` (delivered separately in the conversation)
- App display name: **NoteApple**
- Original package ID retained: `com.inotesapp.inotes`
- Original app version: 2.2.4 (build 1093)
- Target SDK: 36; minimum SDK: 24
- Split set: base, ARM64, English, French, and MDPI density
- UI layout, styling, icon artwork, colors, and app behavior are unchanged except the system app label.
- Hebrew localization resources `he-IL.json` and `he.json` were added to Flutter assets and registered in `AssetManifest.bin`.
- The Hebrew locale was checked against all 1,186 leaf strings in the original `en-US.json`; every key is present and interpolation placeholders are preserved.

## Voice transcription

Version 2.2.4 already contains Google's on-device speech-recognition transcription flow. The transcription screens and language/model messages were included in the Hebrew localization. Whether transcription can run depends on the Android device and the speech-recognition model/service available for the selected language.

## Premium and licensing

This patch does **not** remove purchase checks, bypass subscriptions or lifetime purchases, alter Google Play Billing, or grant paid entitlements. Premium features continue to require a valid purchase or entitlement.

## Signing

All five APKs inside the delivered XAPK were signed with one new NoteApple RSA/SHA-256 APK Signature Scheme v2 certificate. The private PKCS#12 keystore is delivered separately to the user and is not stored in this public repository. Keep the keystore and its password safe; future updates to this package must be signed with the same key.

Certificate SHA-256:
`CC:72:CC:9F:A6:21:98:D5:20:C3:61:74:2D:34:48:E6:CF:9D:43:9C:34:EA:AD:BA:7B:DF:B2:8C:97:E9:E1:86`

## Important installation note

Because the modified package retains the original package ID but uses a new signing certificate, Android will not install it as an in-place update over the official app. Back up notes first; uninstalling the official build may remove its local data. This XAPK also contains ARM64 splits, so it is intended for ARM64 Android devices.

The delivered binary is the installable artifact; this repository records the patch metadata and handoff information. The original proprietary Flutter source is not part of this repository.
