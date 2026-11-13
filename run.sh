#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
BUILD_DIR="build/classes"
mkdir -p "$BUILD_DIR"

echo "Compiling OOPProof-Java..."
javac -Xlint:all -Werror -d "$BUILD_DIR" $(find src -name "*.java")

echo "Running OOPProof-Java benchmark suite..."
java -cp "$BUILD_DIR" dev.oopproof.Main demo --root "$ROOT_DIR"
