#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s <language> <version> <parser-archive> <extract-archive> [output-dir]\n' "$0" >&2
}

[[ $# -ge 4 && $# -le 5 ]] || { usage; exit 2; }

LANGUAGE="$1"
VERSION="$2"
PARSER_ARCHIVE="$3"
EXTRACT_ARCHIVE="$4"
OUTPUT_DIR="${5:-$PWD/dist}"

case "$LANGUAGE" in
  javascript|typescript|js|ts) LANGUAGE="javascript"; PARSER_COMMAND="parser-js"; EXTRACT_COMMAND="extract-js" ;;
  java) PARSER_COMMAND="parser-java"; EXTRACT_COMMAND="extract-java" ;;
  go) PARSER_COMMAND="parser-go"; EXTRACT_COMMAND="extract-go" ;;
  python) PARSER_COMMAND="parser-python"; EXTRACT_COMMAND="extract-python" ;;
  php) PARSER_COMMAND="parser-php"; EXTRACT_COMMAND="extract-php" ;;
  kotlin) PARSER_COMMAND="parser-kotlin"; EXTRACT_COMMAND="extract-kotlin" ;;
  swift) PARSER_COMMAND="parser-swift"; EXTRACT_COMMAND="extract-swift" ;;
  *) printf 'Unsupported language: %s\n' "$LANGUAGE" >&2; exit 2 ;;
esac

[[ -f "$PARSER_ARCHIVE" ]] || { printf 'Parser archive not found: %s\n' "$PARSER_ARCHIVE" >&2; exit 1; }
[[ -f "$EXTRACT_ARCHIVE" ]] || { printf 'Extractor archive not found: %s\n' "$EXTRACT_ARCHIVE" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

unpack() {
  local archive="$1"
  local destination="$2"
  mkdir -p "$destination"
  tar -xzf "$archive" -C "$destination"
  local entries=("$destination"/*)
  if [[ ${#entries[@]} -eq 1 && -d "${entries[0]}" ]]; then
    printf '%s\n' "${entries[0]}"
  else
    printf '%s\n' "$destination"
  fi
}

PARSER_ROOT="$(unpack "$PARSER_ARCHIVE" "$WORK_DIR/parser")"
EXTRACT_ROOT="$(unpack "$EXTRACT_ARCHIVE" "$WORK_DIR/extract")"
[[ -x "$PARSER_ROOT/bin/$PARSER_COMMAND" ]] || { printf 'Parser package does not contain bin/%s\n' "$PARSER_COMMAND" >&2; exit 1; }
[[ -x "$EXTRACT_ROOT/bin/$EXTRACT_COMMAND" ]] || { printf 'Extractor package does not contain bin/%s\n' "$EXTRACT_COMMAND" >&2; exit 1; }

NAME="codegraph-tools-$LANGUAGE-$VERSION-linux-x64"
STAGE="$WORK_DIR/$NAME"
mkdir -p "$STAGE/bin" "$STAGE/skills"
cp -a "$PARSER_ROOT" "$STAGE/parser"
cp -a "$EXTRACT_ROOT" "$STAGE/extract"
ln -s "../parser/bin/$PARSER_COMMAND" "$STAGE/bin/$PARSER_COMMAND"
ln -s "../extract/bin/$EXTRACT_COMMAND" "$STAGE/bin/$EXTRACT_COMMAND"
if [[ -d "$EXTRACT_ROOT/skills" ]]; then
  cp -a "$EXTRACT_ROOT/skills/." "$STAGE/skills/"
fi

sed \
  -e "s/__LANGUAGE__/$LANGUAGE/g" \
  -e "s/__PARSER_COMMAND__/$PARSER_COMMAND/g" \
  -e "s/__EXTRACT_COMMAND__/$EXTRACT_COMMAND/g" \
  "$SCRIPT_DIR/install-language-bundle.sh" > "$STAGE/install.sh"
sed \
  -e "s/__LANGUAGE__/$LANGUAGE/g" \
  -e "s/__PARSER_COMMAND__/$PARSER_COMMAND/g" \
  -e "s/__EXTRACT_COMMAND__/$EXTRACT_COMMAND/g" \
  "$SCRIPT_DIR/start-language-bundle.sh" > "$STAGE/start.sh"
chmod 0755 "$STAGE/install.sh" "$STAGE/start.sh"
printf '%s\n' "$VERSION" > "$STAGE/VERSION"
printf 'Language: %s\nParser: %s\nStatic extract: %s\n' "$LANGUAGE" "$PARSER_COMMAND" "$EXTRACT_COMMAND" > "$STAGE/CONTENTS"

mkdir -p "$OUTPUT_DIR"
tar -C "$WORK_DIR" -czf "$OUTPUT_DIR/$NAME.tar.gz" "$NAME"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUTPUT_DIR/$NAME.tar.gz" > "$OUTPUT_DIR/$NAME.tar.gz.sha256"
else
  shasum -a 256 "$OUTPUT_DIR/$NAME.tar.gz" > "$OUTPUT_DIR/$NAME.tar.gz.sha256"
fi
printf '%s\n' "$OUTPUT_DIR/$NAME.tar.gz"
