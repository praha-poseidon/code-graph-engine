#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="${1:-dev}"
EXTRACTOR_DIR="${STATIC_EXTRACT_JAVA_DIR:-$ROOT_DIR/../static-extract-java}"
NAME="parser-java-${VERSION}-linux-x64"
STAGE="$ROOT_DIR/target/release/$NAME"
DIST="$ROOT_DIR/dist"
JRE_MODULES="java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.compiler,jdk.unsupported,jdk.zipfs"

test -f "$EXTRACTOR_DIR/pom.xml" || { echo "static-extract-java sibling is required" >&2; exit 1; }
(cd "$EXTRACTOR_DIR" && mvn -B -DskipTests -Djacoco.skip=true install)
(cd "$ROOT_DIR" && mvn -B -pl code-graph-parser-java-jdt -am package)
rm -rf "$STAGE"
mkdir -p "$STAGE/bin" "$STAGE/app" "$DIST"
cp "$ROOT_DIR/code-graph-parser-java-jdt/target/parser-java.jar" "$STAGE/app/parser-java.jar"
jlink --add-modules "$JRE_MODULES" --strip-debug --no-header-files --no-man-pages \
  --compress=2 --output "$STAGE/runtime"
install -m 0755 "$ROOT_DIR/packaging/parser-java/parser-java" "$STAGE/bin/parser-java"
install -m 0755 "$ROOT_DIR/packaging/parser-java/install.sh" "$STAGE/install.sh"
printf '%s\n' "$VERSION" > "$STAGE/VERSION"
java -version > "$STAGE/RUNTIME-VERSIONS" 2>&1
tar -C "$ROOT_DIR/target/release" -czf "$DIST/$NAME.tar.gz" "$NAME"
sha256sum "$DIST/$NAME.tar.gz" > "$DIST/$NAME.tar.gz.sha256"
printf '%s\n' "$DIST/$NAME.tar.gz"
