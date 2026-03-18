# KrionTTS

[![Alpha Release](https://img.shields.io/badge/alpha-2026--03--14-orange)](https://github.com/GeorgeSakketos/KrionTTS/releases/tag/alpha-2026-03-14)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blueviolet)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.x-7F52FF)
![License](https://img.shields.io/badge/legal-included-brightgreen)

> Offline Text-to-Speech for Android, powered by sherpa-onnx and built with Kotlin + Jetpack Compose.

![KrionTTS header](icons/Header_Logo.png)

## Why KrionTTS? 🚀

KrionTTS focuses on fast, private, on-device speech generation.
After a model is downloaded, synthesis runs locally with no cloud dependency.

## Highlights ✨

- 📴 Fully offline inference after model download
- 🧠 On-device TTS runtime via sherpa-onnx
- 🌍 Curated multi-language model catalog
- 👥 Speaker selection for multi-speaker models
- 📥 One installed model per language (managed automatically)
- 🔊 Generate and play speech in-app
- 💾 Export to WAV and MP3
- 📂 Save output directly to the Downloads folder

## Tech Stack 🛠️

- Kotlin + Coroutines
- Jetpack Compose (Material 3)
- Android SDK 35, min SDK 26
- sherpa-onnx Android AAR
- OkHttp (model downloads)
- Apache Commons Compress (tar.bz2 extraction)
- TAndroidLame (WAV -> MP3 conversion)

## Project Structure 📁

```text
app/src/main/java/com/krion/tts/
├── MainActivity.kt
├── domain/
│   ├── AudioExporter.kt
│   ├── LanguageModel.kt
│   ├── ModelCatalog.kt
│   ├── ModelRepository.kt
│   └── OfflineTtsManager.kt
└── ui/
    ├── KrionScreen.kt
    ├── KrionUiState.kt
    └── KrionViewModel.kt
```

## Getting Started ⚙️

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 17
- Android SDK with API 35

### Run in Android Studio

1. Open the project.
2. Wait for Gradle sync to complete.
3. Select a device/emulator (Android 8.0+).
4. Run the app.

### Build from CLI

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Generated APKs are placed under app/build/outputs/apk.

## Release Notes 📦

### Alpha Channel

The current alpha pre-release is published on GitHub Releases:

- Release page: https://github.com/GeorgeSakketos/KrionTTS/releases/tag/alpha-2026-03-14
- APK: https://github.com/GeorgeSakketos/KrionTTS/releases/download/alpha-2026-03-14/KrionTTS-alpha-2026-03-14.apk

Note: the alpha APK is intended for testing.

## Model & Runtime Notes 📚

- Catalog sources include open sherpa-onnx compatible model families (MMS, Piper, Coqui where available).
- Archive extraction handles non-canonical package layouts and normalizes required model files.
- Language installation policy keeps one active model per language.
- Speaker preferences are persisted per installed model.
- Internet permission is used for model downloads only.

## Legal & Compliance

- Privacy policy: PRIVACY_POLICY.md
- Terms of use: TERMS_OF_USE.md
- Third-party notices: THIRD_PARTY_NOTICES.md
- In-app legal access: Settings -> Legal

### Release checklist (store distribution)

- Publish the privacy policy URL in the app store listing.
- Complete the store Data Safety form based on actual release behavior.
- Re-verify dependency/model licenses for the exact shipped versions.
