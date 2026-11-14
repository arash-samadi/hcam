#!/bin/sh
# SPDX-FileCopyrightText: The hcam project
# SPDX-License-Identifier: Apache-2.0
#
# Verify the locally supplied HTC shaders and LUT/mask images.

set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ASSETS="$ROOT/app/src/main/assets/htc"
MANIFEST="$ROOT/docs/ASSETS.sha256"

if [ ! -d "$ASSETS" ]; then
    echo "error: $ASSETS does not exist" >&2
    echo "See docs/ASSETS.md for the required layout." >&2
    exit 1
fi

cd "$ASSETS"
sha256sum -c "$MANIFEST"
echo "HTC assets verified."
