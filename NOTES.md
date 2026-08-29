# DankNull 1.7.10 backport — deferred work

Findings from the code-quality review pass that were deliberately **not** applied, kept here so they
aren't lost. Nothing in this file is a known crash or data-loss bug — those were fixed.

## Product decisions (need a call, not just a code change)

### Bucket handling is broader than upstream
`items/ItemDankNull.onItemUse` accepts any `IFluidContainerItem` holding >= 1000 mB, where upstream only
matched bucket-shaped `UniversalBucket`. On a GTNH pack that means GT cells and similar will empty their fluid
into the world when selected and right-clicked. Defensible as a feature, but it is a way to lose a valuable
fluid by accident. Restricting it is a one-line change in `isFilledBucket`.

### Glint colour for the DIAMOND tier
`client/render/DankNullItemRenderer.java` — upstream's constant is `0xFF476666`, which looks like a typo for
`0xFF006666`. Copied verbatim rather than silently "fixing" it.

## Code quality (safe, just not done yet)

### Efficiency
- **`DankNullDockItemRenderer`** deep-copies the docked /dank/null's entire NBT **every frame** (via
  `getDockedDankNull` -> `loadItemStackFromNBT`), and because the result is a fresh object each time it misses
  the identity-keyed handler cache every frame too. Memoise the docked stack against the container stack.
- **`HUDRenderer.drawText`** does ~15 `StatCollector` lookups, several linear map scans and an
  `OreDictionary.getOreIDs` allocation **per frame**. Cache the rendered lines, invalidated on selection change.
- **`GuiDankNull.drawScreen`** performs 3-4 reflective `Field.get` calls *per slot* (~300/frame) for four values
  that are identical across the whole frame, allocates a `SlotPreview` per slot, and deep-copies an `ItemStack`
  per filled /dank/null slot. Hoist the reflection out of the loop; reuse one mutable preview.
- **`JsonItemModel.render`** allocates a `QuadContext` (and therefore a `new Random()`) three times per drawn
  /dank/null per frame. Make it a field.
- **`DankNullItemRenderer`** rebuilds the model-name string and re-does a map lookup per frame; cache per tier.
- **`DankNullHandler.oreMatches`** recomputes the incoming stack's ore names inside a 54-iteration loop, on
  every item pickup. Hoist it; short-circuit on `isOre(storedStack)` first.
- **`TileDankNullDock.canInsertItem`** runs a full simulated insert, which the hopper then immediately repeats
  non-simulated. Three O(slots) scans per hopper attempt.

### Simplification
- `DankNullHandler`'s ore / extraction-mode / placement-mode set+get+cycle triples are the same code three
  times (~90 LOC), and `DankNullSerializer.read` open-codes the same find-or-insert policy three more times.
  The serializer blocks can simply call the handler's own setters (`load()` already suppresses write-back).
- `extractItem` and `extractItemIngoreExtractionMode` differ by one line.
- `GuiDankNull.drawVanillaSlot` / `drawDankNullSlot` are the same 28 lines twice.
- `RecipeDankNullUpgrade.matches` has shifted-match machinery for a grid that cannot shift — the offset loops
  always run exactly once.
- `ModRecipes` has five near-identical upgrade-recipe blocks that are one loop.
- `ModGuiHandler.getClientGuiElement` can delegate to `getServerGuiElement` and just wrap the result.
- Three separate implementations of "find the player's /dank/null slot"
  (`DankNullUtils`, `ModEvents`, `ModGuiHandler`).
- Dead members: `ModGlobals.TIME` (write-only — the whole `tickEvent` handler exists only to advance it),
  `ItemDankNull`'s texture constants, `IDankNullHandler.isLockingSupported/isOreSupported/isSlotEmpty`,
  `PlayerSlot.fromBuff/toBuff`, `ModGuiHandler.GUIType`, `RecipeDankNullUpgrade.getWidth/getHeight`,
  `DankNullItemModes.getMessage`, and the unreachable `renderingContained` recursion guard.
- Stale `TODO(net)` / `TODO(render)` / `TODO(gui)` markers sitting above completed code.
- `network/PacketSetDankNullInDock` and `PacketEmptyDock` are registered but never sent — the vanilla tile
  description packet replaced them. Delete before shipping (deleting later is a protocol break).

### Altitude
- `ContainerDankNull.slotClick` handles only click modes 0 and 1 for /dank/null slots and returns bare `null`
  for drag / throw / hotbar-swap / double-click. The client has already predicted those locally, so the
  divergence persists visually. Cheap fix: resync the cursor and slot instead of returning silently.
- `ItemDankNull.onItemUse`'s generic branch hand-rolls what `ItemBlock.onItemUse` already does. Delegating
  (as the slab branch already does) would pick up modded block rotation and `onBlockPlacedBy` for free, and
  would likely dissolve the slab special case. Behaviour change — needs testing.
- `SlotHotbar`'s locked-slot index is decided once at construction by object identity, so it can end up
  guarding the wrong slot if the /dank/null moves.
- Dock NBT layout is split across `ItemBlockDankNullDock` (read) and `BlockDankNullDock` (write); the tile
  should own both halves.

## Deliberately correct as-is — do not "fix"
- The `WeakHashMap` handler cache in `DankNullUtils`. It restores the one-handler-per-stack guarantee that
  1.12's capability system provided for free. `StackDankNullHandler.container` **must** stay a
  `WeakReference` — a strong reference there reaches from the map's value back to its own key and makes every
  entry immortal.
- `ModRenderers`' per-client-tick re-registration of the dock item renderer. GTNHLib re-registers
  `ModelISBRH` for every modeled block on each resource reload, from a listener registered in postInit — so
  every "just register later" alternative loses that race. Polling is the robust option here.
- The dock's virtual input slot for hopper insertion.
- The bucket and slab branches of `onItemUse` delegating to vanilla rather than reimplementing.
- `GuiDankNull`'s guarded reflection into `GuiContainer` private fields (SRG name first, MCP second — dev runs
  MCP-named, production runs SRG-named).

## Remaining parity gaps vs upstream 1.12 (from the final parity audit)

All the high-impact findings were fixed. These are what is knowingly left.

### Optional-mod features not ported (integrations are out of scope)
- **Extra Utilities 2 angel-block placement** — upstream `ItemDankNull.onItemRightClick` places an angel block in
  mid-air when one is selected and XU2 is loaded. XU2 exists for 1.7.10, so this is implementable, not forced.
- **Chisel variant line** in the GUI tooltip (upstream `GuiDankNull` `dn.chisel_varient.desc`). The lang key is
  still shipped and currently unused.
- **Thaumcraft aspects** on the GUI tooltip (upstream `GuiDankNull`, shift-held).

### Cosmetic / edge-case
- **Hovered-slot highlight** is a flat 50% white wash; upstream used a top-to-bottom gradient
  (`drawGradientRect(..., -2130706433, 2457)`). Note the backport matches *vanilla* here — upstream is the outlier.
- **Ore-dict tooltip/O-click gating** when the ore blacklist AND whitelist are *both* non-empty: upstream ORs the
  two conditions, the backport short-circuits on the blacklist. Consistent within the backport; only that one
  combination diverges.
- **Middle-click pick** uses `Block.getPickBlock(target, world, x, y, z)`; upstream used the player-aware overload.
  A modded block overriding only the player-aware form would be picked incorrectly. Rare on 1.7.10.
- **`railBed` ore-dict registration** — upstream registers `Blocks.LOG` (wildcard) and `Blocks.BEDROCK` as
  `railBed` in preInit. Almost certainly a debug leftover, but it is a real global-registry difference that other
  mods' recipes could see.
- **`FMLMissingMappingsEvent` remap** of legacy `danknull:dank_null` / `dank_null_panel` ids. Portable, but no
  1.7.10 world can contain those ids, so it is moot.

### Deliberate divergences worth signing off
- **`CreativeWhitelist` / `CreativeBlacklist` are now enforced.** Both lists were parsed and never consulted in
  upstream too, so the options filtered nothing. They are now applied in `DankNullHandler.isItemValid`, gated on
  the creative tier only. The meta-optional form (`modid:name`) is parsed as a wildcard meta so it matches the
  item at any damage value, which is what the config description promises.
- **`AllowDockInsertion` is now actually enforced.** Upstream defines the option and never reads it, so setting it
  to false does nothing there; here it really does block hopper/pipe insertion. Behaviour now matches the option's
  own description.
- **Slabs merge into double slabs** when placed from a /dank/null. Upstream has the code for this but it is dead —
  its live path is the generic `placeBlockAt`, which never merges.
- **Item pickup no longer voids the remainder.** Upstream zeroed the stack whenever a matching slot merely existed,
  deleting anything that did not fit and claiming the pickup even when nothing moved.
- **Dock facing is stored in block metadata.** Upstream has no facing at all. Currently inert — the blockstate uses
  a match-all variant, so nothing renders it.
- **Config GUI exposes the `Server Rules` category**; upstream's config screen showed only the client category.
- **Config sync works on an integrated/LAN host**, and synced server values now actually take effect on the client.
  Both were broken upstream (`@SideOnly(Side.SERVER)` stripping, and parsed lists that were never invalidated).

### Known-inert in both trees (not regressions)
- **`isLocked()` is never enforced** — it is read only by the GUI button label and the serializer. Same upstream.
- **The dock emits no comparator signal** despite `canConnectRedstone` returning true. Same upstream.
- **Ore-equivalent items are never absorbed off the ground**: the pickup handler only inserts into slots returned
  by `findItemStacks` (exact matches), even though `isOreDictFiltered` makes the /dank/null eligible. Same upstream.
