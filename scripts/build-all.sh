#!/bin/bash
# Builds and checks the addon for every supported Minecraft / BBS FS pair and collects the jars
# into dist/. Each target is also compiled against the other BBS releases of its series, so an
# API difference between, say, 2.6 and 2.6.1 fails the build instead of a player's game.
#
# Usage: scripts/build-all.sh [target...]    (default: every file in targets/)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILDS="${BBS_BUILDS:-$ROOT/../bbs-fs-builds}"
cd "$ROOT"

targets=("$@")

if [ ${#targets[@]} -eq 0 ]; then
    for file in targets/*.properties; do
        targets+=("$(basename "$file" .properties)")
    done
fi

# Extra BBS builds of the same series every target must also compile against.
compat_builds() {
    case "$1" in
        1.20.1-bbs2.6) echo "2.6-1.20.1" ;;
        1.20.4-bbs2.6) echo "2.6-1.20.4" ;;
        1.21.11-bbs2.6) echo "2.6-1.21.11" ;;
        *) echo "" ;;
    esac
}

rm -rf dist
mkdir -p dist

for target in "${targets[@]}"; do
    echo "== $target"
    ./gradlew --console=plain -q -Ptarget="$target" checkCanvas collectJar

    for build in $(compat_builds "$target"); do
        jar="$BUILDS/$build/build/libs/bbs-$build.jar"

        if [ -f "$jar" ]; then
            echo "   compiles against $build"
            ./gradlew --console=plain -q -Ptarget="$target" -Pbbs_jar="$jar" compileClientJava
        else
            echo "   skipped $build: $jar not built (scripts/prepare-bbs.sh $build)"
        fi
    done
done

ls -la dist
