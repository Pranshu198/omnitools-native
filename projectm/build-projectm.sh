#!/usr/bin/env bash
# Build libprojectM (v4.1.8, static) + the libomniviz.so JNI shim for
# Android with NDK r27c. Mirrors native/build-so.sh conventions.
#
# Output: projectm/libs/<abi>/libomniviz.so  (projectM static-linked in;
# the only runtime deps are Android system libs: GLESv3, EGL, log, dl, m,
# and the NDK's libc++_static, which is linked statically too).
#
# Usage: bash build-projectm.sh [arm64-v8a|armeabi-v7a|all]   (default: all,
# arm64 first; a v7a failure is logged but does not fail arm64's result)
set -uo pipefail
cd "$(dirname "$0")"

NDK="${NDK_HOME:-$HOME/workspace/toolchain-ndk/android-ndk-r27c}"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"
SRC="$PWD/src"
LOG="$PWD/build.log"
: > "$LOG"

build_abi() {
    local ABI="$1"
    local BUILD="$PWD/build-$ABI"
    local PREFIX="$BUILD/install"
    echo "===== $ABI: cmake configure =====" | tee -a "$LOG"
    cmake -S "$SRC" -B "$BUILD" -G "Unix Makefiles" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM=android-26 \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DBUILD_SHARED_LIBS=OFF \
        -DENABLE_SYSTEM_PROJECTM_EVAL=OFF \
        -DENABLE_SYSTEM_GLM=OFF \
        -DENABLE_PLAYLIST=OFF \
        -DENABLE_GLES=ON \
        -DBUILD_TESTING=OFF \
        -DBUILD_DOCS=OFF \
        >> "$LOG" 2>&1 || { echo "$ABI CONFIGURE FAILED (see build.log)"; return 1; }

    echo "===== $ABI: build libprojectM =====" | tee -a "$LOG"
    cmake --build "$BUILD" -j"$(nproc)" >> "$LOG" 2>&1 \
        || { echo "$ABI BUILD FAILED (see build.log)"; return 1; }
    cmake --install "$BUILD" >> "$LOG" 2>&1 \
        || { echo "$ABI INSTALL FAILED (see build.log)"; return 1; }

    echo "===== $ABI: link libomniviz.so =====" | tee -a "$LOG"
    local API=""
    [ "$ABI" = "arm64-v8a" ] && API="aarch64-linux-android26"
    [ "$ABI" = "armeabi-v7a" ] && API="armv7a-linux-androideabi26"
    mkdir -p "$PWD/libs/$ABI"
    # Static libs produced by the install step (names verified against
    # the install tree at build time; adjust here if upstream renames).
    # v4.1.8 installs a single libprojectM-4.a with the vendored eval
    # objects already merged in (linking libprojectM_eval.a separately
    # gives duplicate-symbol errors — verified 2026-11-15).
    local LIBS=("$PREFIX/lib/libprojectM-4.a")
    echo "static libs: ${LIBS[*]}" | tee -a "$LOG"
    "$BIN/${API}-clang++" -shared -fPIC -O2 -Wall -static-libstdc++ \
        -I"$PREFIX/include" \
        -o "$PWD/libs/$ABI/libomniviz.so" \
        "$PWD/jni/omniviz.cpp" \
        -Wl,--whole-archive "${LIBS[@]}" -Wl,--no-whole-archive \
        -lGLESv3 -lEGL -llog -ldl -lm \
        >> "$LOG" 2>&1 || { echo "$ABI SHIM LINK FAILED (see build.log)"; return 1; }
    "$BIN/llvm-strip" --strip-unneeded "$PWD/libs/$ABI/libomniviz.so"
    ls -la "$PWD/libs/$ABI/libomniviz.so"
}

WHICH="${1:-all}"
FAIL=0
if [ "$WHICH" = "all" ] || [ "$WHICH" = "arm64-v8a" ]; then
    build_abi arm64-v8a || FAIL=1
fi
if [ "$WHICH" = "all" ] || [ "$WHICH" = "armeabi-v7a" ]; then
    build_abi armeabi-v7a || echo "armeabi-v7a failed (non-fatal; arm64 is the shipping ABI)"
fi
exit $FAIL
