#!/usr/bin/env bash
#
# Fetches the pinned llama.cpp source that the native build compiles against.
#
# It is deliberately not a git submodule: the SDK is ~400 MB of history nobody
# needs, and pinning a release tag keeps the JNI code in llama_jni.cpp matched to
# a known include/llama.h. Bump LLAMA_TAG (and re-check that header) to upgrade.
set -euo pipefail

LLAMA_TAG="${LLAMA_TAG:-b11799}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$APP_DIR/third_party/llama.cpp"

if [ -f "$DEST/CMakeLists.txt" ]; then
    echo "llama.cpp already present at $DEST"
    exit 0
fi

mkdir -p "$(dirname "$DEST")"
echo "Cloning llama.cpp $LLAMA_TAG into $DEST"
# core.longpaths matters on Windows: llama.cpp ships a web UI whose paths exceed
# MAX_PATH, and a partial checkout would break the native build in odd ways.
git -c core.longpaths=true clone --depth 1 --branch "$LLAMA_TAG" \
    https://github.com/ggml-org/llama.cpp "$DEST"
echo "Done. Re-run when you want to change LLAMA_TAG."
