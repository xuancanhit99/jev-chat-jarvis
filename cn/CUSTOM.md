# Custom Android build

This fork's `feature/i18n-custom` branch keeps an English UI on the upstream Android app in `cn/`, with replies in the incoming message's language, including Vietnamese. The application ID is `com.jev.probe.custom`, so it can coexist with the original app. Current custom version: `1.7-custom.1` (10008).

The custom patch keeps a manual OCR result card visible through window events in apps without a dedicated adapter. Changing the target conversation still clears the previous snapshot.

## Build

Run commands from `cn/` after configuring an Android SDK in the ignored `local.properties` file:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

The custom APK is `cn/app/build/outputs/apk/debug/app-debug.apk` relative to the repository root. The APK in `cn/apk/` is the author's original release, not this custom build. Debug upgrades require the same signing key; retain the Android debug keystore outside the repository.

## Upgrading from the older custom app

Version 1.7 registers its accessibility service as `com.jev.probe.capture.ChatCaptureService`. After installing, enable the custom app's accessibility service again in Android settings, because older builds registered a different service component. Keep the same application ID and signing key to preserve the app's private settings and history.

WhatsApp now has a dedicated adapter for one-to-one chats. It uses the accessible text tree and disables screenshot OCR inside WhatsApp. QQ, X and Lark follow upstream's current support scope. Apps without an adapter, including Zalo, Messenger and Telegram, use the manual OCR path where Android permits it; sender attribution is limited and direct input writes are not verified, so use explicit Copy and paste. Sending always remains manual.

## Repository layout and updates

- `cn/`: the existing app, including this fork's customizations.
- `global/`: upstream's independent English WhatsApp app. It has its own build and package; it does not inherit the custom app's NewAPI settings.
- `.github/`: upstream overview READMEs and shared assets.

Fetch and merge `upstream/main` into `feature/i18n-custom`, resolve changes while retaining upstream behavior, run the relevant builds/tests, then push this branch to `origin`. Never commit API keys, local SDK configuration or signing files.
