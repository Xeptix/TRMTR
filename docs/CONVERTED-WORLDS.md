# Bringing a world over from The Roads More Travelled

milkucha's [The Roads More Travelled](https://github.com/milkucha/trmt) wears ground by replacing blocks: a worn lawn is
an `eroded_grass_block`, then `eroded_dirt`, then `eroded_coarse_dirt`, and worn sand is `eroded_sand`. TRMT: Reimagined
never writes a block into the world, so those blocks mean nothing to it. It reads them instead, and turns
each one back into the plain block it was worn from, with its wear kept.

## Which games

| Minecraft | Loaders |
|---|---|
| 1.20.1 | Forge, Fabric |
| 1.21.1 | NeoForge, Forge, Fabric |
| 26.1.2, 26.2, 26.3 | NeoForge, Forge, Fabric |

A world made with the original's 0.5.1 converts in this mod's edition for the same Minecraft version. A 26.1 world also
opens in 26.2 and 26.3, through the game's own two upgrade questions. 1.7.10, 1.12.2 and 1.16.5 never convert: the
original has no edition for them.

## How to do it

1. **Keep a copy of the world.** The conversion is one way. Once a converted part of the world is saved, the original's
   blocks are gone from it, and on 26.3 the game's own upgrade renames what they were saved under.
2. **Take The Roads More Travelled out of the `mods` folder** and put TRMT: Reimagined in. With the original still
   installed nothing converts, and the log says its blocks are left to it.
3. **Open the world.** Nothing is done all at once: each part of the world is converted the first time it loads, so a
   region you never visit again stays as it was saved. A part with nothing to convert costs nothing you would notice.

## What you are told

- One line in the log when the world is first opened this way.
- One line in the chat, marked `[TRMT]`, to the first operator or single-player host to join: that the world was made
  with The Roads More Travelled, that its worn paths are carried over as each part loads, how many so far, and to keep
  a copy of the world from before if you may go back to that mod. It is said again at each join until an operator has
  seen it.
- `/trmt status` adds a line: how many chunks and worn squares have been converted, since when, and whether an operator
  has been told.

## What carries over

- **Every worn square, at its gradation.** Each eroded block becomes the plain grass, dirt, coarse dirt or sand it was,
  and its stage becomes the matching point on this mod's run. Where both mods had worn the same square, the more worn
  of the two is kept.
- **Sunk sand stays sunk.** The original sinks its sand by stage; it is drawn sunk to the same depth here.
- **Worn dirt becomes worn grass**, as a lawn walked bare and left would grow back. `conversion.dirtBecomesGrass` turns
  that off.

## What does not

- **The lightness effect and its potions.** The original's `trmt:lightness` effect is dropped. Its two potions, and the
  splash, lingering and tipped-arrow forms of them, load as the same items with no potion in them; nothing crashes. This
  mod's own Draught of Lightness does what the effect did.
- **The original's blocks themselves.** This mod never takes a block of the original's into any of its families, with
  or without the original installed.

## Settings

Both are on the **Conversion** tab of the settings screen, are the server's, and are on as the mod ships:

| Setting | What it does |
|---|---|
| `conversion.enabled` | converts the original's worn blocks as each part of the world loads |
| `conversion.dirtBecomesGrass` | a converted worn dirt square becomes worn grass |

## Keeping the original's feel

The conversion keeps the wear. To keep the way the ground wears and heals as well, turn on **Faithful Mode**
(`presets.faithful`, the first row of the presets tab), which wears and heals grass, dirt, sand, leaves and plants by the
original's own numbers. If your old settings file from the original, `trmt.json`, is in this mod's config folder, the
presets tab also offers **Use your TRMT settings**, which turns Faithful Mode on with that file's figures and lists any
it could not match. The manual's [Faithful Mode](../README.md#faithful-mode) section has the details.
