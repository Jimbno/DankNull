# /dank/null — 1.7.10 backport

A Minecraft **1.7.10 / Forge** backport of [p455w0rd's](https://github.com/p455w0rd/DankNull) 1.12.2
`/dank/null` mod, targeting the GTNH environment.

The `/dank/null` is a portable, tiered, filtering item vacuum and dispenser: it absorbs matching items as
you pick them up, holds far more per slot than a vanilla stack, and can place the selected item straight
out of your hand.

## Requirements

| | |
|---|---|
| Minecraft | 1.7.10 |
| GTNHLib | **≥ 0.11.43** (hard dependency) |
| Angelica | optional (soft dependency) |

## Building

```sh
./gradlew build
```

The mod jar is written to `build/libs/`. Use the **plain** jar (e.g. `danknull-<version>.jar`) for a real
instance — the `-dev` jar is deobfuscated and only works inside the development workspace, and the
`-sources` / `-api` jars are not runtime mods.

Versioning comes from git tags. An untagged checkout builds as `NO-GIT-TAG-SET`; tag a commit (for example
`git tag v0.1.0`) to get a real version stamped into the jar and `mcmod.info`.

## What differs from the 1.12 original

1.7.10 lacks several systems the original relied on, so the equivalent behaviour is rebuilt here:

- **No capabilities.** The inventory handler is serialised onto the `/dank/null` ItemStack's own NBT
  (`inventory/DankNullSerializer`) and reached through `DankNullUtils.getHandler(stack)` instead of
  `stack.getCapability(...)`. The docking station uses the same stack-bound handler, so a dock broken
  mid-use keeps its contents with no extra work.
- **No `IItemHandler`.** That surface is declared directly on `IDankNullHandler`, and container slots
  subclass `Slot` rather than Forge's `SlotItemHandler`.
- **No off-hand**, no `BlockPos`, and no JSON recipe system — recipes are registered in Java, and the
  dock's facing lives in block metadata.
- **No `IThreadListener`.** Packet handlers hand work to the game thread through the task queues in
  `ModEvents`.
- Automation reaches a docked `/dank/null` through a vanilla `ISidedInventory` adapter, using a virtual
  input slot so hoppers can feed slots that already exceed `getInventoryStackLimit()`.

Save data is **not** cross-compatible with a 1.12 `/dank/null`, and cannot be: 1.7.10 reads a stack's `id`
as a numeric short where 1.12 wrote a `"modid:name"` string.

`NOTES.md` records deferred work and product decisions that were deliberately left alone.

## Credits

Original 1.12.2 mod by **p455w0rd** (TheRealp455w0rd). Backport built on the GTNH
[ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10) buildscript.

## Licensing note

`LICENSE` is currently the MIT license inherited from the ExampleMod template and still carries the
template author's copyright line. The upstream `/dank/null` repository ships no license file, so the terms
under which this backport may be redistributed have not been settled — resolve this before publishing
builds.
