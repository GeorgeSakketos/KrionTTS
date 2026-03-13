# KrionTTS

KrionTTS is a modern Android offline Text-to-Speech app built with Kotlin + Jetpack Compose.

## Features

- Modern Compose UI
- Offline playback using local on-device sherpa-onnx inference
- Downloadable open-source model catalog (one installed model per language)
- Model selection workflow per language
- Per-model speaker ID selection
- Export generated speech to:
  - `.wav`
  - `.mp3`
- Saved files are written to the device Downloads folder

## Project structure

- `app/src/main/java/com/krion/tts/domain`
  - model catalog and download repository
  - offline TTS manager
  - audio export pipeline
- `app/src/main/java/com/krion/tts/ui`
  - ViewModel + Compose screen/state
- `app/src/main/java/com/krion/tts/MainActivity.kt`
  - app entry point and dependency wiring

## Build and run

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run on a device/emulator with Android 8.0+.

## Notes

- The downloadable model catalog uses open-source sherpa-onnx TTS model archives and enforces one installed model per language.
- The catalog includes MMS, Piper, and Coqui model families (depending on language availability).
- Model extraction normalizes archive layouts so Piper/Coqui packages with non-canonical filenames can still install.
- Multi-speaker options are included (for example Piper LibriTTS-R and Coqui VCTK for English).
- The catalog is curated as multi-speaker-first per language; single-speaker models are kept only when no multi-speaker option is available for that language.
- Current bundled language options include multiple model choices per supported language (English, Spanish, German, French, Russian, and Ukrainian), plus additional families such as Italian, Portuguese, Dutch, Turkish, Swedish, Indonesian, and Arabic; Thai is currently available via MMS.
- Synthesis runs locally on-device through `sherpa-onnx-1.12.28.aar` (no cloud dependency after model download).
- MP3 export converts generated WAV output through FFmpegKit.
