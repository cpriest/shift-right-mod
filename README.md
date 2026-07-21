# shift-right

A NeoForge mod that makes shift-click (quick-move) put items in the **same predictable place every time** — across vanilla containers, modded blocks, and ME-style storage terminals (AE2, Refined Storage).

Default order: **hotbar (1→9), then main inventory (top-left → bottom-right)**.

## Why

Vanilla and modded containers disagree on where shift-clicked items land — some fill the hotbar first, some the top inventory row, some in reverse. This normalizes all of them to one configurable order.

## Requirements

- Minecraft **1.21.1**, NeoForge 21.1.x (any 21.1 build; compiled/tested against 21.1.235). Verified pack: Contained Opolis; ATM10-generation packs share this target.
- Support for other MC versions still targeted by major packs (newer 1.21.x minors; possibly 1.20.1/ATM9) is planned — see PLAN §11 for the roadmap and per-version risk notes.
- **Install on the server** (and in singleplayer). All hooks run server-authoritatively; a client-only install on a server without the mod does nothing useful. Install on both sides for correct client-side prediction (the server-side config syncs to clients on login).

## How it works

One ordering policy, many adapters (see [PLAN.md](PLAN.md) for the full design):

1. **Vanilla-helper containers** (chests, furnaces, most simple modded blocks) — a mixin wraps the slot lookups inside `AbstractContainerMenu#moveItemStackTo` so its two merge passes *visit* slots in policy order. Vanilla still does all merge math (`mayPlace`, max stack sizes, return semantics); the slot list itself is never reordered, so there is nothing to desync. Ranges containing no player-inventory slots (e.g. moving items *into* a chest) are left completely untouched.
2. **Vanilla give-path** (`Inventory.add` / `player.addItem`) — many mods with custom `quickMoveStack` logic delegate "find a home" to `Inventory#getFreeSlot()` / `getSlotWithRemainingSpace()`. An optional mixin reorders those scans. Note these also fire on ground pickup and dispenser-style insertion; with the default hotbar-first/forward policy the scan order equals vanilla's, so you'll only notice under `MAIN_FIRST`/`REVERSE`. Toggle: `enableVanillaAddPathMixin`.
3. **AE2 terminals** (ME / Crafting / Pattern Access / Wireless / Portable) — AE2's SHIFT_CLICK and MOVE_REGION paths funnel through `AEBaseMenu#getQuickMoveDestinationSlots`, which returns an ordered list of vanilla slots. An optional `@Pseudo` mixin reorders that list; AE2's own powered extraction and merging are reused untouched. No hard dependency — the adapter is dormant unless AE2 is present.

Every adapter fails safe: on any signature mismatch or unexpected state it logs once and falls back to the mod's native behavior. Never crash, never lose items.

### Refined Storage status

Per PLAN §6, the first step is determining whether RS even needs its own adapter: RS inserts extracted items into the player inventory through an insertable-storage wrapper rather than an ordered slot list. On NeoForge that wrapper resolves through the player-inventory item handler (slots 0→35, i.e. already hotbar-first) and/or the vanilla add path covered by hook #2 above. **No RS-specific mixin ships yet** — verify against the pack's RS version before writing one (the `enableRefinedStorageAdapter` config key is reserved for it). If your RS grid fills in the wrong order with this mod installed, please open an issue with the RS version.

## Configuration

Server-side config (`serverconfig/shiftright-server.toml` per world; synced to clients):

| Key | Default | Meaning |
| --- | --- | --- |
| `fillOrder` | `HOTBAR_FIRST` | `HOTBAR_FIRST` or `MAIN_FIRST` — which section fills first |
| `direction` | `FORWARD` | `REVERSE` flips the whole order (hotbar 9→1, main bottom-right→top-left) |
| `enableAe2Adapter` | `true` | Reorder AE2 terminal quick-move destinations |
| `enableRefinedStorageAdapter` | `true` | Reserved (see Refined Storage status above) |
| `enableVanillaAddPathMixin` | `true` | Reorder `getFreeSlot`/`getSlotWithRemainingSpace` scans |

## Known limitations

- **Pickup-All** (double-click / double shift-click collection) uses separate vanilla logic and still fills in vanilla order. (Same limitation Consistent Shift documents.)
- Within one `quickMoveStack`, a menu decides *which slot ranges to try in which order* itself; the core hook normalizes order **within** each range. Since almost all menus pass the whole player inventory as one range, this covers the dominant visible issue, but a menu that deliberately tries "main inventory range, then hotbar range" in two separate calls keeps that range preference.
- Per-mod adapters (AE2, RS) are version-sensitive by nature; they are gated, defensive, and fall back to native behavior on any mismatch.

## Development workflow (hot reload)

Iterate in the **dev client**, not your installed pack — the dev client supports hot reload; a launcher pack does not.

1. `./gradlew runClient` launches a dev client with this mod loaded from sources. Add `-PdevMods` to include third-party mods (AE2 preconfigured; add CurseForge mods via the CurseMaven lines in `build.gradle` — e.g. Sophisticated Backpacks — filling in the file ids from your pack).
2. Run the `runClient` configuration from IntelliJ in **Debug** mode. Edit code, hit Build (Ctrl+F9), and JVM HotSwap reloads changed **method bodies** into the running game — no restart, no world reload.
3. The mixins are thin one-line trampolines into plain classes (`QuickMoveReorder`, `SlotOrders`, policies, adapters) precisely so that hot reload covers the real logic. What does **not** hot reload: mixin annotations/targets, `shiftright.mixins.json`, `neoforge.mods.toml` — those need a client restart.
4. Optional upgrade: select JetBrains Runtime as the run config JVM and add `-XX:+AllowEnhancedClassRedefinition` to also hotswap added/removed methods and fields.
5. For final verification in your real pack: `./gradlew build`, drop `build/libs/shiftright-<version>.jar` into the pack's `mods/` folder, relaunch (no hot reload there).

Most iteration shouldn't need a client at all — `./gradlew test` runs the menu-level tests with mixins applied in seconds; see Automated testing below.

## Building

```
./gradlew build        # jar in build/libs/
./gradlew test         # pure unit tests for the ordering policy
./gradlew runClient    # dev client
./gradlew runServer    # dev server
```

Requires Java 21 and network access to `maven.neoforged.net` on first run. Versions are pinned in `gradle.properties` (`minecraft_version`, `neo_version`) — adjust there to match your pack.

## Automated testing

Everything this mod does is server-side logic, so nearly all of it is verified headlessly in CI — no client needed:

1. **Pure JUnit** — the ordering policy layer is Minecraft-free and tested directly (permutation validity, policy order, reverse, broken-policy rejection).
2. **Menu-level JUnit** (`./gradlew test`, via ModDevGradle's FML JUnit launch) — tests run with real Minecraft classes, registries bootstrapped, **and this mod's mixins applied**. `QuickMoveIntegrationTest` shift-clicks through a real `ChestMenu` and asserts exact slot placement. Since vanilla fills the hotbar right-to-left (slot 9 first) and this mod fills left-to-right (slot 1 first), the assertions prove the mixin is active — not just that items moved. Covers top-up ordering, non-stackables, overflow, conservation, into-chest untouched, and `MAIN_FIRST` via a test-only config override.
3. **GameTests** (`./gradlew runGameTestServer`) — a headless in-world server runs `QuickMoveGameTests`, which drive `AbstractContainerMenu#clicked` with `QUICK_MOVE` against a real chest block entity — the exact server entry point of a player's shift-click packet. The server exits non-zero on failure, so CI catches regressions.
4. **AE2 contract check** (`ci/check-ae2-contract.sh`, separate CI job) — downloads the latest AE2 release for our MC version from Modrinth and verifies via `javap` that `AEBaseMenu#getQuickMoveDestinationSlots(ItemStack, boolean)` still exists returning `List`. If AE2 ever renames it, CI fails loudly instead of the adapter silently no-oping in-game.

What CI **cannot** cover: real-input feel (shift+scroll, double-click Pickup-All), AE2/RS behavior against a live grid, and client↔server desync under latency. PLAN §8 has the manual matrix for those; the RS verification in particular is still an in-game task.

## License

MIT — see [LICENSE](LICENSE).
