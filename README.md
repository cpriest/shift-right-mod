# shift-right-mod

A NeoForge mod that makes shift-click (quick-move) put items in the **same predictable place every time** — across vanilla containers, modded blocks, and ME-style storage terminals (AE2, Refined Storage).

Default order: hotbar (1→9), then main inventory (top-left → bottom-right).

## Status
Early / in design. See [PLAN.md](PLAN.md) for the full build spec.

## Why
Vanilla and modded containers disagree on where shift-clicked items land — some fill the hotbar first, some the top inventory row, some in reverse. This normalizes all of them to one configurable order.

## Scope
- **Vanilla + simple modded containers** — via the shared `moveItemStackTo` transfer path.
- **Mods using the vanilla give-path** (`Inventory.add`) — via `getFreeSlot` / `getSlotWithRemainingSpace`.
- **AE2 / Refined Storage terminals** — via optional, version-gated per-mod adapters.

## Requirements
- Minecraft 1.21.x, NeoForge.
- **Install on the server** (and singleplayer). Behavior is server-authoritative; a client-only install on a vanilla server does nothing.

## Configuration
Server-side config: fill order (hotbar-first / main-first), direction (forward / reverse), and per-adapter toggles for AE2 and Refined Storage.

## Known limitations
- Vanilla **Pickup-All** (double shift-click) still uses vanilla ordering.
- Per-mod adapters (AE2, RS) are version-sensitive and fail safe — they fall back to the mod's native behavior on any mismatch.

## License
MIT, see License
