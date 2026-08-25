#!/usr/bin/env bash
set -euo pipefail
PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="$(cat "$PACKAGE_DIR/VERSION")"
INSTALL_ROOT="${CODEGRAPH_INSTALL_ROOT:-$HOME/.local/share/codegraph}"
BIN_DIR="${CODEGRAPH_BIN_DIR:-$HOME/.local/bin}"
TARGET="$INSTALL_ROOT/parser-java/$VERSION"
mkdir -p "$TARGET" "$BIN_DIR"
cp -R "$PACKAGE_DIR/." "$TARGET/"
ln -sfn "$TARGET/bin/parser-java" "$BIN_DIR/parser-java"
printf 'Installed parser-java to %s\nCommand: %s/parser-java\n' "$TARGET" "$BIN_DIR"
