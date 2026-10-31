#!/usr/bin/env bash
# Builds and tests both supported Minecraft targets and collects the jars in dist/ with checksums.
# Usage (repo root):  bash tools/build-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf dist && mkdir -p dist
for target in 26.2 26.3; do
  echo "=== Minecraft $target ==="
  ./gradlew clean build -Pmc_target="$target" --console=plain
  jar="$(ls build/libs/aetherion-*+"$target".jar | grep -v sources | head -n1)"
  cp "$jar" "dist/"
  cp "$(ls build/libs/aetherion-*+"$target"-sources.jar | head -n1)" "dist/"
done
(cd dist && sha256sum *.jar > SHA256SUMS.txt && cat SHA256SUMS.txt)
