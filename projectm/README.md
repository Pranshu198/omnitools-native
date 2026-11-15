# projectM / MilkDrop visualizations — Track C (Phase 3)

## What is here

| Path | What |
|---|---|
| `src/` | projectM **v4.1.8** source (git tag `v4.1.8`, commit `4967dc0`), incl. the `vendor/projectm-eval` submodule (`e8c311e`). LGPL-2.1 — see `src/COPYING` / `src/LICENSE.txt`. |
| `jni/omniviz.cpp` | JNI shim → `libomniviz.so`. projectM is **static-linked** into it; runtime deps are Android system libs only (GLESv3, EGL, log, dl, m, c). |
| `build-projectm.sh` | Reproducible build: `bash build-projectm.sh [arm64-v8a\|armeabi-v7a\|all]`. NDK r27c, CMake "Unix Makefiles", `ANDROID_PLATFORM=android-26`, `ANDROID_STL=c++_static`, `BUILD_SHARED_LIBS=OFF`, `ENABLE_SYSTEM_PROJECTM_EVAL=OFF`, `ENABLE_PLAYLIST=OFF`, `ENABLE_GLES=ON`. |
| `build.log` | Log of the last script run. |
| `libs/<abi>/libomniviz.so` | Built libraries (also staged into `nativelibs/lib/<abi>/`, where build-native.sh picks them up automatically). |
| `INTEGRATION.md` | The exact manifest / player-hook / proguard / assets steps for the coordinator. |

Java side: `src/com/jackbhai/omnitools/nativ/viz/` — `ProjectMBridge`
(JNI), `VizActivity` (fullscreen GLSurfaceView + controls), `VizRenderer`,
`AudioFeed` (Visualizer capture → PCM), `PresetCatalog` (bundled +
Downloads/OmniPresets + SAF folder), `FallbackVizRenderer` (4 built-in
OpenGL modes: Spectrum, Wave, Particles, Kaleidoscope).

Presets: `extra-assets/presets/` — 126-preset starter pack (see
INTEGRATION.md for why it lives in extra-assets, and the pack's license
note in `extra-assets/presets/PACK-LICENSE.md`).

## Verified build results (2026-11-15, this machine)

- `libs/arm64-v8a/libomniviz.so` — **2,142,496 bytes** stripped, ELF64
  AArch64, 11 exported `Java_com_jackbhai_omnitools_nativ_viz_*` symbols,
  NEEDED = system libs only, **zero** undefined projectM symbols.
- `libs/armeabi-v7a/libomniviz.so` — **1,384,904 bytes** stripped, ELF32
  ARM, same 11 JNI symbols.
- Java: `javac` (JDK 17, android-34 jar + repo `libs/*.jar`) compiles the
  whole `viz/` package clean, exit 0.
- Build wall found & fixed: v4.1.8 installs a single `libprojectM-4.a`
  with the vendored eval objects already merged — linking
  `libprojectM_eval.a` separately fails with duplicate symbols. The
  script links only `libprojectM-4.a`, `--whole-archive`.
- Shim must link `-static-libstdc++`, otherwise the .so gains a
  `libc++_shared.so` NEEDED and the APK would have to ship that too.

## NOT proven here (needs a device)

- Actual on-screen rendering (headless proof is impossible: projectM
  needs a live GLES3 context). First on-device check: open VizActivity
  while a song plays; the preset name overlay + auto-advance confirm
  the engine; if `projectm_create` fails, VizActivity degrades to the
  built-in modes with a note instead of crashing.
- Audio reactivity strength: the Android Visualizer API caps capture
  at 20 Hz / 1024 samples, so projectM receives chunked PCM, not a
  continuous stream (every Visualizer-based Android port has this
  limit). If beats feel soft on device, raise Sensitivity (bridge maps
  it to `projectm_set_beat_sensitivity`).
- GLSurfaceView is created with EGL context client version 3
  (projectM 4 requires GLES 3). API 26+ devices effectively all have it.
