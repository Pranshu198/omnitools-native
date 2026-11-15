#!/usr/bin/env bash
# Compiles and runs the update module's JVM tests (Versions is pure Java,
# no Android or org.json needed). Adapted from the staging module script.
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$HOME/workspace/toolchain/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
OUT=build/test-classes
rm -rf build && mkdir -p "$OUT"
javac -encoding UTF-8 -source 17 -target 17 -d "$OUT" \
    ../../src/com/jackbhai/omnitools/nativ/update/Versions.java \
    UpdateTestMain.java
java -cp "$OUT" UpdateTestMain
