#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LANGUAGE="__LANGUAGE__"
VERSION="$(cat "$PACKAGE_DIR/VERSION")"
INSTALL_ROOT="${CODEGRAPH_INSTALL_ROOT:-$HOME/.local/share/codegraph}"
BIN_DIR="${CODEGRAPH_BIN_DIR:-$HOME/.local/bin}"
SKILLS_DIR="${CODEGRAPH_SKILLS_DIR:-${CODEX_SKILLS_DIR:-$HOME/.codex/skills}}"
TARGET="$INSTALL_ROOT/tools/$LANGUAGE/$VERSION"

mkdir -p "$TARGET" "$BIN_DIR"
cp -a "$PACKAGE_DIR/." "$TARGET/"

ln -sfn "$TARGET/bin/__PARSER_COMMAND__" "$BIN_DIR/__PARSER_COMMAND__"
ln -sfn "$TARGET/bin/__EXTRACT_COMMAND__" "$BIN_DIR/__EXTRACT_COMMAND__"

if [[ -d "$PACKAGE_DIR/skills" ]]; then
  mkdir -p "$SKILLS_DIR"
  while IFS= read -r -d '' skill_dir; do
    skill_name="$(basename "$skill_dir")"
    rm -rf "$SKILLS_DIR/$skill_name"
    cp -a "$skill_dir" "$SKILLS_DIR/$skill_name"
    printf 'Installed Skill: %s/%s\n' "$SKILLS_DIR" "$skill_name"
  done < <(find "$PACKAGE_DIR/skills" -mindepth 1 -maxdepth 1 -type d -print0)
fi

printf 'Installed %s tools to %s\n' "$LANGUAGE" "$TARGET"
printf 'Commands: %s/%s, %s/%s\n' "$BIN_DIR" "__PARSER_COMMAND__" "$BIN_DIR" "__EXTRACT_COMMAND__"
printf 'Run ./start.sh to let an Agent generate endpoint-rule ZIP files.\n'
