#!/bin/bash
# Builds the BBS FS jars the addon compiles against, straight from ../bbs-fs.
# Each version is exported with `git archive` into ../bbs-fs-builds/<version>, so the bbs-fs
# checkout itself (its branch, working tree and build folder) is never touched.
#
# Usage: scripts/prepare-bbs.sh [version...]    (default: every version below)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BBS_REPO="${BBS_REPO:-$ROOT/../bbs-fs}"
OUT="${BBS_BUILDS:-$ROOT/../bbs-fs-builds}"

# version -> commit of github.com/Wemppy4/bbs-fs
declare -a VERSIONS=(
    "2.6-1.20.4:5b913c52b29521687f79712b580ce1365a04d140"
    "2.6.1-1.20.4:fb6795eeb74d5504c3576b302f322451f1adf54b"
    "2.6-1.20.1:a4babb685651ea61313f8bcde1aef3f0ad83a15f"
    "2.6.1-1.20.1:3b36bad693c02207c66801caeb3f09859a30da52"
    "2.7-1.20.1:998c9d585427b4cf0061940496ae1f6a7df1bb03"
    "2.7-1.20.4:13bd62dab1980b974b8ea0f78d037e8e82684b84"
    "2.6-1.21.11:f66cdcb7b"
    "2.6.1-1.21.11:8808a3b856e19ed8337a1b4759518313bbd28898"
    "2.7-1.21.11:9fb7f936ad563b6299fba84ab2dbd211b4776543"
)

wanted=("$@")
mkdir -p "$OUT"
git -C "$BBS_REPO" fetch --quiet origin || echo "warning: could not fetch $BBS_REPO, using the commits already there"

for entry in "${VERSIONS[@]}"; do
    name="${entry%%:*}"
    commit="${entry#*:}"

    if [ ${#wanted[@]} -gt 0 ] && [[ ! " ${wanted[*]} " =~ " $name " ]]; then
        continue
    fi

    dir="$OUT/$name"
    jar="$dir/build/libs/bbs-$name.jar"

    if [ -f "$jar" ] && [ "$(cat "$dir/.bbs-commit" 2>/dev/null)" = "$(git -C "$BBS_REPO" rev-parse "$commit")" ]; then
        echo "== $name: up to date ($jar)"
        continue
    fi

    echo "== $name: exporting $commit"
    rm -rf "$dir"
    mkdir -p "$dir"
    git -C "$BBS_REPO" archive "$commit" | tar -x -C "$dir"
    git -C "$BBS_REPO" rev-parse "$commit" > "$dir/.bbs-commit"

    echo "== $name: building"
    (cd "$dir" && chmod +x gradlew && ./gradlew --no-daemon -q remapJar -x test)
    ls -la "$jar"
done
