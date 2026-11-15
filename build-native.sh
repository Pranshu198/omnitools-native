#!/usr/bin/env bash
# Manual native build for OmniTools Native (no Gradle) — same pipeline
# shape as the spike / omnitools-app builds: aapt2 -> javac -> R8 ->
# zipalign -> apksigner. Signs with the OmniTools RELEASE keystore (the
# same identity as the WebView app) but installs as a SEPARATE app:
# package com.jackbhai.omnitools.native.
#
# The PWA dist is synced into assets/ at build time (git-ignored), exactly
# like the v2 WebView app: Games + Menu host those bundled pages.
#
# TWO-PASS INTEGRITY SEAL: pass 1 builds the APK; the SHA-256 of its
# classes*.dex + resources.arsc is sealed into libomnisec.so (native/
# gen_hashes.py); pass 2 rebuilds with identical Java inputs (the seal
# lives only in native code, never in the dex — so the dex is
# byte-identical) and the script FAILS LOUDLY if the final APK's hash
# does not match the sealed value.
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-$HOME/workspace/toolchain/jdk17}"
SDK="${ANDROID_SDK:-$HOME/workspace/android-sdk}"
R8_JAR="${R8_JAR:-$HOME/workspace/toolchain-dl/r8.jar}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

VERSION_CODE=1
VERSION_NAME="3.0-native.1"
APK_NAME="OmniTools-${VERSION_NAME}.apk"
KS_DIR="${OMNI_KS_DIR:-$HOME/workspace/omnitools-app/keystore}"
NDK_HOME="${NDK_HOME:-$HOME/workspace/toolchain-ndk/android-ndk-r27c}"

build_once() {
    # ---- bundle the PWA: Menu + Games host the built pages ----
    if [ "${SKIP_PWA_SYNC:-0}" != "1" ]; then
        PWA_DIST="${PWA_DIST:-$HOME/workspace/omnitools/dist}"
        if [ ! -f "$PWA_DIST/index.html" ]; then
            echo "ERROR: PWA dist not found at $PWA_DIST" >&2; exit 1
        fi
        echo "== sync PWA bundle ($PWA_DIST -> assets) =="
        rm -rf assets
        mkdir -p assets
        cp -r "$PWA_DIST/." assets/
        SKIP_PWA_SYNC=1   # second pass reuses the same sync
    fi
    echo "bundled files: $(find assets -type f | wc -l), bytes: $(du -sb assets | cut -f1)"

    OUT=build/manual
    rm -rf "$OUT"
    mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/rescompiled" "$OUT/nativelibs"

    echo "== aapt2 compile (library AAR res + app res) =="
    RESZIPS=()
    for d in aarx/*/; do
        if [ -d "$d/res" ] && [ -n "$(ls -A "$d/res" 2>/dev/null)" ]; then
            n="$(basename "$d")"
            "$BT/aapt2" compile --dir "$d/res" -o "$OUT/rescompiled/$n.zip"
            RESZIPS+=("$OUT/rescompiled/$n.zip")
            echo "  compiled res: $n"
        fi
    done
    "$BT/aapt2" compile --dir res -o "$OUT/rescompiled/app.zip"

    echo "== aapt2 link =="
    EXTRA_PACKAGES="androidx.annotation.experimental:androidx.core:androidx.core.ktx:androidx.customview.poolingcontainer:androidx.lifecycle.runtime:androidx.media:androidx.media3.exoplayer:androidx.media3.session:androidx.media3.ui:androidx.recyclerview:eightbitlab.com.blurview:com.eightbitlab.blurview:com.gauravk.audiovisualizer:com.masoudss.lib:com.airbnb.lottie:androidx.fragment:androidx.viewpager:androidx.loader:androidx.activity:androidx.lifecycle.viewmodel:androidx.lifecycle.viewmodel.savedstate:androidx.lifecycle.livedata.core:androidx.savedstate:androidx.vectordrawable:androidx.vectordrawable.animated:androidx.tracing:com.bumptech.glide"
    "$BT/aapt2" link -o "$OUT/app-res.apk" \
        --auto-add-overlay \
        -I "$ANDROID_JAR" \
        --manifest AndroidManifest.xml \
        --java "$OUT/gen" \
        --custom-package com.jackbhai.omnitools.nativ \
        --extra-packages "$EXTRA_PACKAGES" \
        -A assets \
        --min-sdk-version 26 --target-sdk-version 34 \
        --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
        "${RESZIPS[@]}" "$OUT/rescompiled/app.zip"

    echo "== javac =="
    find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
    CP="$ANDROID_JAR"
    for j in libs/*.jar; do CP="$CP:$j"; done
    javac -encoding UTF-8 -source 17 -target 17 \
        -cp "$CP" -d "$OUT/classes" @"$OUT/sources.txt"
    (cd "$OUT/classes" && jar cf "../classes.jar" .)

    if [ "${FAST:-0}" = "1" ]; then
        echo "== D8 fast dex (NO minify/obfuscation — iteration build only) =="
        rm -rf "$OUT/merged"; mkdir -p "$OUT/merged"
        (cd "$OUT/merged" && jar xf ../classes.jar && for j in ../../../libs/*.jar; do jar xf "$j"; done && jar cf ../merged.jar .)
        java -cp "$R8_JAR" com.android.tools.r8.D8 \
            --min-api 26 \
            --lib "$ANDROID_JAR" \
            --output "$OUT/dex" \
            "$OUT/merged.jar"
    else
    echo "== R8 (minify + obfuscate; app rules + every AAR consumer proguard.txt) =="
    PG_ARGS=(--pg-conf proguard-native.pro --pg-conf webkit-proguard.txt)
    for f in aarx/*/proguard.txt; do
        [ -f "$f" ] && PG_ARGS+=(--pg-conf "$f")
    done
    java -cp "$R8_JAR" com.android.tools.r8.R8 \
        --release --min-api 26 \
        --lib "$ANDROID_JAR" \
        "${PG_ARGS[@]}" \
        --output "$OUT/dex" \
        "$OUT/classes.jar" libs/*.jar
    fi
    ls -la "$OUT/dex"

    echo "== package + align =="
    cp "$OUT/app-res.apk" "$OUT/app-unsigned.apk"
    (cd "$OUT/dex" && zip -q "../app-unsigned.apk" *.dex)
    # Native libraries: AAR jni dirs (e.g. Amplituda) + the project's own
    # nativelibs/ (libomnisec). Compressed; extractNativeLibs default
    # handles them, and the APK stays smaller than with stored entries.
    ABS_OUT="$PWD/$OUT"
    stage_so() {
        local src_dir="$1"
        (cd "$src_dir" && find . -name '*.so' -print0 | while IFS= read -r -d '' so; do
            rel="${so#./}"
            mkdir -p "$ABS_OUT/nativelibs/lib/$(dirname "$rel")"
            cp "$rel" "$ABS_OUT/nativelibs/lib/$rel"
        done)
    }
    for d in aarx/*/jni; do [ -d "$d" ] && stage_so "$d"; done
    [ -d nativelibs/lib ] && stage_so nativelibs/lib
    if [ -d "$OUT/nativelibs/lib" ]; then
        (cd "$OUT/nativelibs" && zip -q "../app-unsigned.apk" -r lib)
        echo "  native libs added: $(find "$OUT/nativelibs" -name '*.so' | wc -l)"
    fi
    "$BT/zipalign" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

    echo "== sign with the OmniTools release keystore =="
    KS_PASS="$(cat "$KS_DIR/keystore-password.txt")"
    OMNI_KS_PASS="$KS_PASS" "$BT/apksigner" sign \
        --ks "$KS_DIR/omnitools-release.jks" \
        --ks-key-alias omnitools \
        --ks-pass env:OMNI_KS_PASS \
        --key-pass env:OMNI_KS_PASS \
        --out "$APK_NAME" \
        "$OUT/app-aligned.apk"
}

apk_hash() {
    python3 -c "
import hashlib, re, zipfile, sys
md = hashlib.sha256()
with zipfile.ZipFile('$APK_NAME') as z:
    for n in sorted(x for x in z.namelist() if re.fullmatch(r'classes\d*\.dex', x)):
        md.update(z.read(n))
    md.update(z.read('resources.arsc'))
print(md.hexdigest())
"
}

# ---- native security library (if the NDK is available) ----
HAVE_NDK=0
if [ -x "$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" ]; then
    HAVE_NDK=1
fi
if [ "$HAVE_NDK" = "1" ]; then
    if [ "${FAST:-0}" = "1" ]; then
        # Iteration builds must never embed a stale seal: force the
        # unsealed (all-zero) header so the dex check stays permissive.
        python3 native/gen_hashes.py
    fi
    [ -f native/sec_hashes.h ] || python3 native/gen_hashes.py
    echo "== build libomnisec.so (pass 1: unsealed or previous seal) =="
    NDK_HOME="$NDK_HOME" bash native/build-so.sh
else
    echo "== NDK not found: building WITHOUT libomnisec (Java fallbacks apply) =="
fi

echo "########## PASS 1 ##########"
build_once

if [ "${FAST:-0}" = "1" ]; then
    echo "FAST build: integrity seal SKIPPED by design (iteration only)."
    echo "The shippable release MUST be built without FAST=1."
elif [ "$HAVE_NDK" = "1" ]; then
    echo "== seal dex+arsc hash into libomnisec.so =="
    SEALED="$(python3 native/gen_hashes.py "$APK_NAME" | sed 's/.*: //')"
    NDK_HOME="$NDK_HOME" bash native/build-so.sh
    echo "########## PASS 2 (sealed) ##########"
    build_once
    FINAL="$(apk_hash)"
    echo "sealed: $SEALED"
    echo "final:  $FINAL"
    if [ "$SEALED" != "$FINAL" ]; then
        echo "ERROR: integrity seal MISMATCH — pass-2 dex/arsc differ from pass 1." >&2
        echo "The build is NOT shippable: the on-device dex check would false-lock." >&2
        exit 1
    fi
    echo "INTEGRITY SEAL OK — final APK hashes match the sealed values."
fi

echo "== verify =="
"$BT/apksigner" verify --print-certs "$APK_NAME" | head -4
"$BT/aapt" dump badging "$APK_NAME" | head -4
ls -la "$APK_NAME"
echo "BUILD OK -> $APK_NAME"
