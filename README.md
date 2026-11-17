# NoteApple — Hebrew localization patch

This repository tracks the Hebrew resource patch for **Notes - iOS Style Notepad 2.2.4** by Baday Production.

The earlier experimental Kotlin/Compose project was removed from the current branch. The delivered APK is built from the supplied compiled Flutter XAPK; it is not a replacement implementation and does not include the original proprietary Flutter source.

## Installable build

- File: `NoteApple.apk` (delivered separately in the conversation)
- Display name: NoteApple (Hebrew assets included)
- Original package ID: `com.inotesapp.inotes`
- Version: `2.2.4-he` (version code 1093)
- Target SDK 36; minimum SDK 24
- ARM64 only
- Standalone APK: merged base + ARM64 native libraries + MDPI resources
- Root manifest `requiredSplitTypes` was cleared after merging the required ABI and density content.
- Every native `.so` payload is stored and aligned to 16 KiB.
- Signed with APK Signature Scheme v2, RSA PKCS#1 v1.5 + SHA-256 (algorithm ID `0x0103`).
- APK Signature v2 digest and RSA signature were independently verified; ZIP CRC and required Flutter Hebrew assets passed local checks.

## Localization

- Hebrew localization resources `he-IL.json` and `he.json` are embedded and registered in Flutter `AssetManifest.bin`.
- The Hebrew file covers the 1,186 English leaf strings checked in the source locale, with interpolation placeholders preserved.
- Screen layout, visual style, colors, icon artwork, and app behavior are otherwise unchanged, apart from the system app label.

## Voice transcription

Version 2.2.4 already contains Google's speech-recognition transcription flow. The Hebrew interface strings were localized. Actual Hebrew recognition depends on the speech service and language model available on the Android device; the recognition engine itself was not replaced.

## Premium and licensing

Purchase checks, Google Play Billing, and entitlement handling are unchanged. This patch does not bypass subscriptions or unlock paid features.

## Signing

All delivered NoteApple builds use the same new RSA signing key. Certificate SHA-256:

`CC:72:CC:9F:A6:21:98:D5:20:C3:61:74:2D:34:48:E6:CF:9D:43:9C:34:EA:AD:BA:7B:DF:B2:8C:97:E9:E1:86`

The private PKCS#12 keystore is delivered separately to the user and is not stored in this public repository. Future updates must use the same key.

## Installation caveat

Because the standalone APK uses the same original package ID but a new signing certificate, it cannot update the official Play Store build in place. Back up notes first, uninstall the original app, then install NoteApple. The APK is ARM64-only.

The APK has been structurally checked and its v2 signature verified locally. No physical Android handset was available to perform an on-device installation test.