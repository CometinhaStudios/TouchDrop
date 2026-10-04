#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$test_dir" app/src/main/java/com/cometinha/touchdrop/Wire.java app/src/test/java/com/cometinha/touchdrop/WireTest.java
java -cp "$test_dir" com.cometinha.touchdrop.WireTest
