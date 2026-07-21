# Session hand-off — shift-right

**For:** a local Claude Code CLI session. **Immediate goal:** get `./gradlew runClient` working on the user's machine (they want a dev client for manual testing — their mod + AE2 — with a persistent test world).

**User context:** not a Java developer, no IDE — everything must work from a terminal + any text editor. Plays via GDLauncher. Their pack is Contained Opolis (MC 1.21.1, NeoForge 21.1.235).

## State of the project (all on branch `claude/plan-review-implement-d89eh1`)

The mod is fully implemented per PLAN.md and green in CI (GitHub Actions "Build" workflow, runs 1–5). No PR exists yet. Everything below is *already done — do not redo it*:

- Core mixin (`AbstractContainerMenuMixin`): `@WrapOperation` on the `slots.get(i)` calls in `moveItemStackTo`, reordering slot *visits* to policy order (default hotbar 1→9 then main top-left→bottom-right). Ranges without player slots untouched.
- Add-path mixin (`InventoryMixin`): `getFreeSlot`/`getSlotWithRemainingSpace` scan in policy order, config-gated.
- AE2 adapter (`AEBaseMenuMixin` + `Ae2QuickMoveAdapter`): `@Pseudo`, `require = 0`, reorders `getQuickMoveDestinationSlots` return; no AE2 compile dependency.
- Refined Storage: deliberately **no** mixin (PLAN §6 analysis says the item-handler/add path likely covers it on NeoForge); config key reserved; needs in-game verification.
- Server-type config (`ShiftRightConfig`): `fillOrder`, `direction`, adapter toggles; synced to clients; safe defaults pre-load; has a test-override seam.
- Four CI test tiers, all passing: pure policy JUnit, FML-JUnit menu tests **with mixins applied** (`QuickMoveIntegrationTest` — the hotbar-slot-0-vs-8 assertions prove the mixin fires), in-world GameTests via `runGameTestServer`, and `ci/check-ae2-contract.sh` (Modrinth latest AE2 + javap signature check).
- `deployJar` task (`-PmodsDir=...`) for copying the jar into a GDLauncher instance.
- Multi-version roadmap in PLAN.md §11 (next targets: newer 1.21.x minors, possibly 1.20.1/Forge for ATM9).

## The task: `./gradlew runClient`

**Important: `runClient` has never been executed anywhere.** The previous session ran in a sandbox whose network policy blocked `maven.neoforged.net`, so *no* Gradle game task ever ran locally there — only in CI, and CI only runs `build`, `test`, and `runGameTestServer`. The toolchain itself is proven (gameTestServer boots a full headless server with the mod + mixins), but client-specific pieces are unexercised. Expected friction points, in likely order:

1. **Plain `./gradlew runClient` first** (no flags). Needs Java 21 (`settings.gradle` has the foojay resolver, so Gradle can auto-provision). First run downloads MC assets (~1 GB) plus the NeoForge dev toolchain — slow once, cached after. Launches as offline "Dev" player; world saves persist in `runs/client/saves/`.
2. **Then `-PdevMods` (AE2).** The AE2 maven coordinate in `build.gradle` is a **best guess, never resolved**: `appeng:appliedenergistics2:19.2.9` from `https://modmaven.dev/`. If resolution fails, browse the modmaven index for the right artifact id (possibly `appliedenergistics2-neoforge`) and a version matching MC 1.21.1 / the user's pack. Update the default in `build.gradle`; `-Pae2Version=` overrides at the command line. Alternative if modmaven is a dead end: CurseMaven (`curse.maven:ae2-223794:<fileId>`) — pattern already documented in the `devMods` block, but verify AE2's real CurseForge project id rather than trusting that number.
3. **Linux display quirks** if the user is on Linux/Wayland: LWJGL/GLFW may need `-Dorg.lwjgl.glfw.libname` tweaks or XWayland — diagnose only if it actually fails.
4. **`-PhotSwap` is experimental and untested**: requires JetBrains Runtime 21 as the JVM Gradle uses (`-XX:HotswapAgent=fatjar` + `-XX:+AllowEnhancedClassRedefinition` are already wired in the client run config, property-gated). Gradle toolchain auto-detection may pick the system Temurin over JBR; if so, look at `org.gradle.java.installations.paths` / disabling auto-detect. Treat as a stretch goal *after* plain runClient works; the user was told the restart loop is the fallback.

Also on the user's wishlist once the client runs: verify AE2 terminal shift-click lands hotbar-first in-game, and check Sophisticated Backpacks behavior (its own container logic — likely covered by the core mixin or the add-path mixin, but unverified; they'd add it via the CurseMaven lines).

## Ground rules carried over

- Branch: develop and push on `claude/plan-review-implement-d89eh1`. Don't create a PR unless asked.
- Fail-safe philosophy everywhere: adapters must never crash or lose items; on any mismatch, log once and fall back to native behavior.
- Don't reorder `AbstractContainerMenu.slots` itself — slot index = `slotNumber` in click packets; reordering desyncs (the mixin reorders *iteration*, not the list).
- Vanilla Pickup-All (double shift-click) ordering is a documented known limitation — don't try to "fix" it as a side quest.
- Keep the mixin handlers as thin trampolines into plain classes — that's what keeps logic hot-reloadable and unit-testable.
- `gradle.properties` pins `neo_version=21.1.235` to match the user's pack; the jar's runtime range is `[21.1.0,)`.

## Quick reference

```
./gradlew test                 # policy + menu-level tests (mixins applied), fast
./gradlew runGameTestServer    # in-world tests, headless
./gradlew runClient [-PdevMods] [-PhotSwap]
./gradlew deployJar -PmodsDir="<instance>/mods"
bash ci/check-ae2-contract.sh 1.21.1
```

Docs: `README.md` (user-facing, incl. dev workflow), `PLAN.md` (design spec, §11 = version roadmap). Delete this HANDOFF.md when it has served its purpose.
