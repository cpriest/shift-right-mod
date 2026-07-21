#!/usr/bin/env bash
# Verifies that every third-party class/method our @Pseudo mixins hook still exists
# with the expected signature, by pulling each mod jar from Modrinth and inspecting
# the bytecode with javap. Catches adapter drift offline instead of in-game (where
# require=0 mixins silently fall back to the mod's native ordering).
#
# Usage:
#   ci/check-compat-contracts.sh            # check the pinned dev versions
#   ci/check-compat-contracts.sh latest     # check the newest release per mod (drift radar)
#   MC_VERSION=1.21.1 ci/check-compat-contracts.sh latest
set -euo pipefail

MC_VERSION="${MC_VERSION:-1.21.1}"
MODE="${1:-pinned}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
FAILURES=0

# Pinned versions must match the devMods/devStorage defaults in build.gradle.
declare -A PINNED=(
    [ae2]="19.2.17"
    [refined-storage]="2.0.9"
    [sophisticated-core]="1.21.1-1.4.77.2173"
    [mouse-tweaks]="1.21-2.26.1-neoforge"
)

fetch_jar() { # slug -> $WORK_DIR/<slug>.jar, echoes resolved version
    local slug="$1" version url
    if [[ "$MODE" == "latest" ]]; then
        local json
        json="$(curl -sfL "https://api.modrinth.com/v2/project/${slug}/version?game_versions=%5B%22${MC_VERSION}%22%5D&loaders=%5B%22neoforge%22%5D")"
        version="$(echo "$json" | jq -r '.[0].version_number')"
        url="$(echo "$json" | jq -r '.[0].files[] | select(.primary) | .url')"
    else
        version="${PINNED[$slug]}"
        url="https://api.modrinth.com/maven/maven/modrinth/${slug}/${version}/${slug}-${version}.jar"
    fi
    curl -sfL -o "$WORK_DIR/${slug}.jar" "$url"
    echo "$version"
}

# check <slug> <class-path> <human hook name> <grep pattern over javap -p -c output>
check() {
    local slug="$1" class="$2" hook="$3" pattern="$4"
    local version
    version="$(fetch_jar "$slug")"
    unzip -q -o -j "$WORK_DIR/${slug}.jar" "${class}.class" -d "$WORK_DIR" 2>/dev/null || {
        echo "FAIL  ${slug} ${version}: class ${class} missing from jar (${hook})"
        FAILURES=$((FAILURES + 1))
        return
    }
    if javap -p -c "$WORK_DIR/$(basename "$class").class" | grep -qE "$pattern"; then
        echo "OK    ${slug} ${version}: ${hook}"
    else
        echo "FAIL  ${slug} ${version}: ${hook} — pattern not found: ${pattern}"
        FAILURES=$((FAILURES + 1))
    fi
}

# AE2 adapter (AEBaseMenuMixin): destination-list choke point.
check ae2 "appeng/menu/AEBaseMenu" \
    "getQuickMoveDestinationSlots(ItemStack, boolean) -> List" \
    'java\.util\.List<.*> getQuickMoveDestinationSlots\(net\.minecraft\.world\.item\.ItemStack, boolean\)'

# RS adapter (RsItemHandlerInsertableStorageMixin): the private insert overload...
check refined-storage "com/refinedmods/refinedstorage/neoforge/storage/ItemHandlerInsertableStorage" \
    "private insert(ItemResource, long, Action, IItemHandler)" \
    'private long insert\(com\.refinedmods\.refinedstorage\.common\.support\.resource\.ItemResource, long, com\.refinedmods\.refinedstorage\.api\.core\.Action, net\.neoforged\.neoforge\.items\.IItemHandler\)'
# ...and the ItemHandlerHelper.insertItem call it wraps.
check refined-storage "com/refinedmods/refinedstorage/neoforge/storage/ItemHandlerInsertableStorage" \
    "invokes ItemHandlerHelper.insertItem" \
    'invokestatic .*ItemHandlerHelper\.insertItem:\(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z\)'

# Sophisticated adapter (StorageContainerMenuBaseMixin): the merge reimplementation...
check sophisticated-core "net/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase" \
    "mergeItemStack(ItemStack, int, int, boolean, boolean, boolean)" \
    'mergeItemStack\(net\.minecraft\.world\.item\.ItemStack, int, int, boolean, boolean, boolean\)'
# ...and the self-owned getSlot(i) lookups we wrap inside it.
check sophisticated-core "net/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase" \
    "invokes own getSlot(I)" \
    'invokevirtual .*Method getSlot:\(I\)Lnet/minecraft/world/inventory/Slot;'

# MouseTweaks adapter (MouseTweaksMainMixin): push-destination search.
check mouse-tweaks "yalter/mousetweaks/Main" \
    "findPushSlots(List, Slot, int, boolean)" \
    'findPushSlots\(java\.util\.List<.*>, net\.minecraft\.world\.inventory\.Slot, int, boolean\)'

echo
if [[ "$FAILURES" -gt 0 ]]; then
    echo "${FAILURES} contract(s) broken — the corresponding require=0 mixin(s) would silently no-op."
    exit 1
fi
echo "All compat contracts hold (${MODE} versions, MC ${MC_VERSION})."
