# shift-right — Build Plan / Handoff Spec

Target repo: `cpriest/shift-right-mod`. Audience: implementing coding agent (competent with NeoForge + Mixin, no prior context).

Intent: make quick-move (shift-click) *destination fill order* consistent across vanilla, modded blocks, and ME-style terminals — items always land in one predictable order (default: hotbar 1→9, then main inventory top-left→bottom-right).

## 0. Strategy
No single interception point covers everything; shift-click is implemented three ways:
1. **Vanilla-helper containers** (chests, furnaces, most simple modded blocks) route through `AbstractContainerMenu.moveItemStackTo(...)`. Fix by controlling iteration order into that helper.
2. **Mods using the vanilla give-path** (`Inventory.add`) resolve target via `Inventory.getFreeSlot()` / `getSlotWithRemainingSpace()`. Reorder those.
3. **ME-style virtual storage** (AE2, RS) run their own server-side extraction and place via their own ordered target logic. Per-mod version-gated adapters.

Principle: **one ordering policy, many adapters.** Define fill order once; every hook consumes it. All server-authoritative (§7) — works singleplayer and on servers with the mod; not client-only-on-vanilla-servers (that's the click-sim architecture, out of scope).

## 1. Target
- NeoForge, MC 1.21.x (confirm exact minor against the pack, pin `neoForgeVersion`).
- Mixin toolchain from the NeoForge MDK.
- AE2 + RS as `modCompileOnly` + `runtimeOnly` for dev. No hard deps; adapters optional, gated by `ModList.isLoaded(...)`.
- Structure so a Fabric target *could* be added (core mixins are loader-agnostic since they hit vanilla classes); don't block on it.

## 2. Ordering policy (write first)
```java
public interface SlotOrderPolicy {
    List<QuickMoveSlot> order(List<QuickMoveSlot> candidates, Context ctx);
}
```
- `QuickMoveSlot` = {role: HOTBAR|MAIN, subIndex, handle}. Adapters wrap native slot/target objects.
- Default `HotbarFirstPolicy`: HOTBAR 0→8, then MAIN row-major. Also provide `MainFirstPolicy` + `reverse` flag.
- Pure, side-effect free. Decides order only; never moves items.
- Config (`ModConfigSpec`, **server-side**): `fillOrder` (HOTBAR_FIRST default | MAIN_FIRST), `direction` (FORWARD | REVERSE), `enableAe2Adapter`, `enableRefinedStorageAdapter`, `enableVanillaAddPathMixin` (all bool, default true).

## 3. Core mixin — vanilla-helper containers
**Target:** `AbstractContainerMenu#moveItemStackTo(ItemStack, int, int, boolean)`. Two passes over `slots[start..end)`: top-up matching, then fill empties (respecting `getMaxStackSize`, `Slot.mayPlace`). Only the iteration order is wrong.

**Do NOT reorder `this.slots`** — a slot's list position is its `slotNumber`, referenced by click packets; reordering desyncs. Reorder *iteration over indices* in `[start,end)`.

Two options:
- **(A)** `@Inject` cancel + reimplement both passes in policy order. Full control, but you own the merge math — get `mayPlace`/`maxStackSize`/return semantics exact or you get item loss.
- **(B)** `@WrapOperation`/`@Redirect` on the loop counter/slot lookup so vanilla's passes visit slots in policy order, vanilla still merges. Lower item-loss risk. **Preferred** if the target bytecode allows.

Caveat in code: reordering within one `[start,end)` range doesn't fix *which range a menu tries first* (that's in each menu's own `quickMoveStack`, multiple `moveItemStackTo` calls). For the player's own inventory, also handle `InventoryMenu#quickMoveStack` (+ creative variant) for cross-range hotbar-first. Third-party block menus: within-range reorder already fixes the dominant visible issue.

**Known limitation (carry over, don't fake-fix):** vanilla Pickup-All (double shift-click) uses separate logic, still fills right→left. Same issue Consistent Shift documents. Put it in the README.

## 4. Vanilla add-path mixin — the long tail
**Targets:** `Inventory#getFreeSlot()` and `Inventory#getSlotWithRemainingSpace(ItemStack)`. Many custom-`quickMoveStack` mods still delegate the "find a home" step to `player.getInventory().add()` / `player.addItem()`, which resolve through these. Reorder their scan → global influence without per-mod code.

Gate behind `enableVanillaAddPathMixin`. These fire in non-shift contexts too (ground pickup, dispensers) — reordering affects those; usually fine, document it, toggle exists. **AE2 and RS do NOT use this path for grid shift-click** — this is for the broad middle of tech/storage mods.

## 5. AE2 adapter (verified against current source)
Path: client `SHIFT_CLICK` → `MEInteractionPacket` → `MEStorageMenu#handleNetworkInteraction` → `moveOneStackToPlayer(AEItemKey)` → **`getQuickMoveDestinationSlots(ItemStack, boolean)`** (ordered `Slot` list) → per slot: placeable amount, powered extract, `Slot#setByPlayer(...)`. `MOVE_REGION` loops `moveOneStackToPlayer`, inherits ordering.

Single choke point: `getQuickMoveDestinationSlots` on `appeng.menu.AEBaseMenu` (shared by all terminals). AE2's extraction/merge reused untouched.

Adapter: `@Pseudo @Mixin(targets="appeng.menu.AEBaseMenu")`, `@ModifyReturnValue` (MixinExtras) on `getQuickMoveDestinationSlots`, gated on `ModList.isLoaded("ae2")` + config. Classify returned slots via AE2's `SlotSemantics.PLAYER_HOTBAR`/`PLAYER_INVENTORY`, run policy, return reordered. Server-side only. **Verify signature against the pinned AE2 version.** Wrap in try/catch → log once → fall back to AE2 default. `tryFillContainerItem`'s move-to-player uses vanilla `addItem`, already covered by §4 — leave it.

## 6. Refined Storage adapter
RS ships loader-agnostic `refinedstorage-common` (mod id `refinedstorage`); target it → covers both loaders. Same shape as AE2: virtual resource list (`ItemResource`/`GridResource`), server-authoritative. Grid menus in `com.refinedmods.refinedstorage.common.grid` (`AbstractGridContainerMenu`, etc.).

Difference: RS inserts to player via an **`InsertableStorage` wrapper around the player inventory**, not an ordered `Slot` list.

Steps (verify class names against the pack's RS version — they move between milestones):
1. From `AbstractGridContainerMenu` extraction handling, follow to the player-inventory insert wrapper (look for `PlayerInventoryInsertableStorage` / a platform storage over `player.getInventory()`, or a `GridExtractionStrategy`).
2. **If it delegates to vanilla `Inventory.add`, §4 already covers RS → §6 is a no-op. Check this first.** Otherwise `@Pseudo` mixin its insert iteration to policy order.
3. Gate on `ModList.isLoaded("refinedstorage")` + config, wrap defensively, fall back on mismatch.

## 7. Server authority & multiplayer (document prominently)
Vanilla `moveItemStackTo`, AE2, and RS grid interactions all run server-side → this is a **common/server-side mod**. Authoritative, desync-free on singleplayer and servers with it installed. On a server *without* it, client-side reordering would desync — don't ship that fallback. README: "install on the server (and singleplayer). Client-only install does nothing useful."

## 8. Testing matrix
Verify hotbar-first (or configured) order, no item loss, no desync:
- Vanilla: chest, double chest, furnace (input/fuel/output ranges), shulker, hopper, brewing stand, crafting result, inventory↔hotbar.
- Edge: 64-stack into partial destinations, non-stackables, `mayPlace`-restricted slots, Pickup-All (still works, no loss).
- A couple simple tech mods using vanilla `add` (§4).
- AE2: ME/Crafting/Pattern Access/Wireless/Portable terminals; SHIFT_CLICK + MOVE_REGION.
- RS: Grid/Crafting Grid/Portable Grid; shift-click + shift+scroll.
- MP dev server with mod: repeat, watch for ghost items.
- GameTest for pure `SlotOrderPolicy`; headless test on `moveItemStackTo` reorder with a mock menu if feasible.

## 9. Consistent Shift — contribution/port assessment
Fabric-only, **CC-BY-ND-4.0** per Modrinth/repo (**confirm the actual `LICENSE` in `github.com/ZipeStudio/Consistent-Shift` before relying on this**). If ND holds:
- **You may NOT distribute a modified version or a port.** A NeoForge fork (public or private) violates the license. Verbatim redistribution is allowed but the Fabric jar won't run on NeoForge, so it doesn't help.
- Compliant routes, preferred order:
  1. **Upstream PR** adding NeoForge/multi-loader, merged + released *by the author* (rights holder distributes → ND not triggered for you). Open an issue first — ND projects often reject structural PRs; ask before investing.
  2. **Ask the author to relicense** or grant port permission. Cheap to ask.
  3. **Clean-room build = shift-right.** Concept isn't protected; code is. Must share zero code with Consistent Shift and not be described as a port. Given ND, this is the safe default, and shift-right is already a superset (adapters, config, server-authoritative).

Recommendation: don't port it. Build shift-right clean; optionally open a courtesy upstream issue offering multi-loader work. Keep the codebases provably independent. (Technically its core is a mixin on the same §3 vanilla path, so logic ports trivially — the work is scaffolding — but moot unless licensing is resolved.)

## 10. Build order
1. Scaffold NeoForge + Mixin + config, loading as no-op.
2. `SlotOrderPolicy` + `HotbarFirstPolicy` + unit test.
3. §3 core mixin (prefer B). Test vanilla matrix — the "80%".
4. §4 add-path mixin behind toggle. Re-test.
5. AE2 adapter (§5) — confirm `getQuickMoveDestinationSlots` signature on pinned version first.
6. RS: *determine* if §4 covers it before writing §6.
7. MP dev-server pass.
8. README: server-side requirement, Pickup-All limitation, config, adapter version-fragility.
9. (Optional) courtesy issue to Consistent Shift.

Every per-mod adapter fails safe: signature mismatch → log once → fall back to native behavior. Never crash, never lose items.

