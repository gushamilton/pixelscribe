# PixelScribe (Offline Clinical Note Taker)

PixelScribe is a local‑first Android app for recording patient consultations, transcribing them fully offline with sherpa‑onnx (NVIDIA Parakeet zipformer), and formatting the transcript with an on‑device LLM via MediaPipe LLM Inference. It is designed around privacy (no data leaves the device) and a simple workflow: record → transcribe → refine → review.

## What this repo contains
- Kotlin + Jetpack Compose app using MVVM.
- Offline speech‑to‑text using sherpa‑onnx transducer models.
- Offline LLM formatting using MediaPipe GenAI (LlmInference).
- Room database for storing consultation history.
- A simple UI with recording, processing, and result states.

## Key workflows
1. **Record**: Audio captured with `MediaRecorder` (AAC in `.m4a`, 16 kHz).
2. **Transcribe**: Audio decoded to PCM and passed to sherpa‑onnx offline recognizer.
3. **Refine**: Raw transcript is formatted using an on‑device LLM with a configurable system prompt.
4. **Review**: The UI shows the final note and raw transcript; history is stored locally.

## Requirements
- Android Studio + Android SDK
- A physical device with enough RAM (tested with Pixel‑class hardware)
- Offline model files placed on device storage (see below)

## Model files (not in git)
Large model binaries are intentionally excluded from the repo. Place them on the device at:

`/sdcard/Download/`

Required files:
- **Sherpa‑onnx (zipformer transducer)**
  - `encoder.int8.onnx`
  - `decoder.int8.onnx`
  - `joiner.int8.onnx`
  - `tokens.txt`
- **LLM (MediaPipe .task)**
  - `gemma-3n-E4B-it-int4.task`

Note: The app currently reads models directly from `/sdcard/Download/` (see `ModelAssets.kt`, `TranscriptionService.kt`, and `LlmManager.kt`).

## Permissions
The app requests:
- `RECORD_AUDIO`
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (pre‑Android 11)
- Manage External Storage access on Android 11+ (to read `/sdcard/Download`)

## Project structure
```
app/src/main/java/com/example/ambientpixel/
  data/        Room entities + DAO + repository
  domain/      AudioRecorderManager, TranscriptionService, LlmManager, ModelAssets
  ui/          Compose screens + theme
  viewmodel/   ConsultationViewModel + state
```

## Run
1. Open the project in Android Studio.
2. Sync Gradle.
3. Place the required model files on the device at `/sdcard/Download/`.
4. Connect your device and run the app.

## Notes
- This project is fully offline at runtime. Any model downloads/conversions are manual.
- The LLM is closed after each generation to reduce memory pressure.

## Repo status
- `.gitignore` excludes build outputs, local IDE files, and large model binaries.

---
If you want changes to the model paths, naming, or on‑device storage strategy, open an issue or adjust `ModelAssets.kt`, `TranscriptionService.kt`, and `LlmManager.kt`.
