AmbientPixel: Run Notes

Required assets (place in app/src/main/assets/):
- encoder.int8.onnx
- decoder.int8.onnx
- joiner.int8.onnx
- tokens.txt
- gemma-2b-it-cpu-int4.bin

Libraries:
- Sherpa-onnx AAR in app/libs/sherpa-onnx-1.12.20.aar
- MediaPipe GenAI dependency in app/build.gradle.kts

Run steps:
1) Open the project in Android Studio.
2) Sync Gradle.
3) Connect a device (Pixel recommended).
4) Run the app.

Notes:
- The app runs fully offline after models are in assets.
- LLM is closed after each generation to reduce memory pressure.
