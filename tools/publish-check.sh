#!/bin/sh
# SPDX-FileCopyrightText: The hcam project
# SPDX-License-Identifier: Apache-2.0
#
# Pre-publication guard. Run from the repository root before pushing.
# This script never deletes anything; it only reports.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

fail=0

if [ ! -d .git ]; then
    echo "warning: not a git repository; skipping tracked-file checks"
    exit 0
fi

tracked_assets=$(git ls-files -- app/src/main/assets/htc)
if [ -n "$tracked_assets" ]; then
    echo "error: HTC assets are tracked:" >&2
    echo "$tracked_assets" >&2
    fail=1
fi

tracked_apk=$(git ls-files -- '*.apk' '*.aab' '*.odex' '*.so')
if [ -n "$tracked_apk" ]; then
    echo "error: build artefacts or native binaries are tracked:" >&2
    echo "$tracked_apk" >&2
    fail=1
fi

tracked_private=$(git ls-files -- 'device-originals/*' 'private/*' 'research-output/*')
if [ -n "$tracked_private" ]; then
    echo "error: private analysis/device files are tracked:" >&2
    echo "$tracked_private" >&2
    fail=1
fi

if [ "$fail" -ne 0 ]; then
    echo "Publication check FAILED." >&2
    exit 1
fi

echo "Publication check passed: no HTC assets, device originals or build artefacts are tracked."
