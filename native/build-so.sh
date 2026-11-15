#!/usr/bin/env bash
# Compile libomnisec.so for the two ARM ABIs (the user's phones are ARM;
# x86/x86_64 are emulator-only and intentionally not shipped — on those,
# Sec falls back to its Java path and the dex check is skipped, by design).
set -euo pipefail
cd "$(dirname "$0")"
NDK="${NDK_HOME:-$HOME/workspace/toolchain-ndk/android-ndk-r27c}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
OUT=../nativelibs/lib
mkdir -p "$OUT/arm64-v8a" "$OUT/armeabi-v7a"
"$BIN/aarch64-linux-android26-clang" -shared -fPIC -O2 -Wall \
    -o "$OUT/arm64-v8a/libomnisec.so" omnisec.c
"$BIN/armv7a-linux-androideabi26-clang" -shared -fPIC -O2 -Wall \
    -o "$OUT/armeabi-v7a/libomnisec.so" omnisec.c
ls -la "$OUT"/*/libomnisec.so
