#!/usr/bin/env bash
# Compiles and runs the backup module's JVM tests. BackupCodec is pure
# Java + org.json; the org.json jar is the one the app's smoke suite uses.
# Adapted from the staging module script.
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$HOME/workspace/toolchain/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
JSON_JAR="${JSON_JAR:-$HOME/workspace/omnitools-native/smoke/json.jar}"
OUT=build/test-classes
rm -rf build && mkdir -p "$OUT"
javac -encoding UTF-8 -source 17 -target 17 -cp "$JSON_JAR" -d "$OUT" \
    ../../src/com/jackbhai/omnitools/nativ/backup/PrefStore.java \
    ../../src/com/jackbhai/omnitools/nativ/backup/BackupCodec.java \
    BackupTestMain.java
java -cp "$OUT:$JSON_JAR" BackupTestMain
