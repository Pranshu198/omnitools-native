#!/bin/sh
# Pure-JVM tests for PlaylistStore (org.json core only).
# PlaylistStore's Android glue needs android.jar only to compile; the core
# paths exercised here never call it.
set -e
cd "$(dirname "$0")"
JDK=~/workspace/toolchain/jdk17
JSON_JAR=../../smoke/json.jar
ANDROID_JAR=~/workspace/android-sdk/platforms/android-34/android.jar
OUT=/tmp/omnitools-playlist-tests
rm -rf "$OUT"; mkdir -p "$OUT"
"$JDK/bin/javac" -encoding UTF-8 -cp "$JSON_JAR:$ANDROID_JAR" -d "$OUT" \
    ../../src/com/jackbhai/omnitools/nativ/Song.java \
    ../../src/com/jackbhai/omnitools/nativ/PlaylistStore.java
"$JDK/bin/javac" -encoding UTF-8 -cp "$JSON_JAR:$OUT" -d "$OUT" PlaylistTestMain.java
"$JDK/bin/java" -cp "$OUT:$JSON_JAR:$ANDROID_JAR" PlaylistTestMain
