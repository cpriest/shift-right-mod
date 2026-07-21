#!/usr/bin/env bash
# Verifies the AE2 method our adapter mixin hooks (PLAN §5) still exists with the
# expected signature in the LATEST AE2 release for the given MC version, by pulling
# the jar from Modrinth and inspecting the class with javap. Catches adapter drift in
# CI instead of in-game (where it would silently fall back to AE2's native ordering).
set -euo pipefail

MC_VERSION="${1:-1.21.1}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Resolving latest AE2 release for MC ${MC_VERSION} (neoforge) from Modrinth..."
VERSIONS_JSON="$(curl -sfL "https://api.modrinth.com/v2/project/ae2/version?game_versions=%5B%22${MC_VERSION}%22%5D&loaders=%5B%22neoforge%22%5D")"

JAR_URL="$(echo "$VERSIONS_JSON" | jq -r '.[0].files[] | select(.primary) | .url')"
AE2_VERSION="$(echo "$VERSIONS_JSON" | jq -r '.[0].version_number')"
if [[ -z "$JAR_URL" || "$JAR_URL" == "null" ]]; then
    echo "ERROR: could not resolve an AE2 jar for MC ${MC_VERSION}" >&2
    exit 1
fi
echo "Latest AE2: ${AE2_VERSION}"

curl -sfL -o "$WORK_DIR/ae2.jar" "$JAR_URL"
unzip -q -o -j "$WORK_DIR/ae2.jar" 'appeng/menu/AEBaseMenu.class' -d "$WORK_DIR"

DECOMPILED="$(javap -p "$WORK_DIR/AEBaseMenu.class")"
MATCH="$(echo "$DECOMPILED" | grep -F 'getQuickMoveDestinationSlots(net.minecraft.world.item.ItemStack, boolean)' || true)"

if [[ -z "$MATCH" ]]; then
    echo "ERROR: AE2 ${AE2_VERSION} no longer declares getQuickMoveDestinationSlots(ItemStack, boolean)." >&2
    echo "The adapter mixin (require = 0) will silently no-op on this AE2 version — update AEBaseMenuMixin." >&2
    echo "Methods found on AEBaseMenu:" >&2
    echo "$DECOMPILED" | grep -i 'quickmove' >&2 || echo "(none containing 'quickmove')" >&2
    exit 1
fi
if ! echo "$MATCH" | grep -q 'java\.util\.List'; then
    echo "ERROR: getQuickMoveDestinationSlots exists on AE2 ${AE2_VERSION} but no longer returns java.util.List:" >&2
    echo "  $MATCH" >&2
    exit 1
fi

echo "AE2 contract OK on ${AE2_VERSION}:"
echo "  ${MATCH#"${MATCH%%[![:space:]]*}"}"
