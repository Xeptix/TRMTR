# Where the editions differ

TRMT Reimagined is one mod with one version number. Every edition does the same thing by the same
numbers and reads the same settings file, and twenty-six classes are shared between them byte for
byte, with a test in each that fails the build if the copies drift.

This page is everything that is *not* the same, in one place. The manual says each of these in the
section it belongs to as well; this is the list for somebody deciding which version to play, or
moving a pack from one to the other.

## Supported versions

| Minecraft | Loader | Status | Needs |
|---|---|---|---|
| **1.7.10** | Forge 10.13.4.1614+ | released | [UniMixins](https://github.com/LegacyModdingMC/UniMixins), or the older [GTNHMixins](https://www.curseforge.com/minecraft/mc-mods/gtnhmixins) |
| **1.12.2** | Forge 14.23.5.2847+ | released | [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) |

Both run on plain Forge with nothing else installed. Every other integration is behind a
mod-loaded check.

## The differences, and why

### Maps — the largest practical difference

| | 1.7.10 | 1.12.2 |
|---|---|---|
| Worn ground coloured per position | yes | yes |
| How | reflection into JourneyMap and Xaero's internals | one block override, no map mod named |
| `surfaces.mapTracksWear` | on, can be switched off | always on, cannot be switched off |
| `surfaces.mapWearDarkening` | works | **does nothing** |
| `client.desirePathHighlight` | works | **does nothing** |
| `client.mapWearThroughTint` | works | **does nothing** |
| Vanilla map item | family colour only | same as every other map |

On 1.7.10 a block is asked its colour with nothing but a metadata value, so a worn square cannot
tell which position is being asked about. Reaching into those two mods' own colour lookups is the
only way to answer per position — and because it hands them a real RGB value, that edition can
darken a road by any fraction and pull a route toward a violet that is not a material at all.

1.12.2 hands the position in. One override answers for every map that draws the world, so both
integrations disappear, there is nothing to go stale when a map mod changes its internals, and the
vanilla map item is served too. The cost is that the answer is one of vanilla's sixty-four fixed
palette entries, and the shade actually drawn is chosen from terrain height rather than by the
block — so there is nothing to darken and no violet to reach for.

The four settings are left in the file on 1.12.2 so a pack can move between versions and find its
edits where it left them. The mod names any of them you have changed, once, as it loads.

### Progression

| | 1.7.10 | 1.12.2 |
|---|---|---|
| Sixteen entries | achievements, on their own page | advancements |
| Translation keys | the same | the same |
| Conditional registration | per switch | read where the advancement is granted |

1.12 removed the achievement system. This is the one place the 1.12.2 edition cannot be 1:1, and it
is a change of mechanism rather than of content: the same sixteen, the same lang keys, so one
translation serves both. A JSON file cannot be registered conditionally, so every switch an
achievement answered to is read at the moment of granting instead.

### Ghost blocks

| | 1.7.10 | 1.12.2 |
|---|---|---|
| Cosmetic blocks registered | **66** | **1** |

Not sixty-six pictures — one block class per shape and per rendering path, because on 1.7.10 a
block's appearance is a metadata value and an icon per side. On 1.12.2 appearance is a blockstate
and a baked model that is handed the position, so one class carries every shape and every
gradation.

This matters in one visible way: deleting the jar makes Forge show its *"found ID mismatches"*
screen once, with sixty-six names to report on 1.7.10 and one on 1.12.2. Your world is intact
either way — none of those blocks is ever placed in the saved world.

### Shaders

`client.inheritShaderMaterial` works on **1.7.10 only**. It works by reaching into the shader mod's
own block-material lookup, and 1.12.2's shaders are a different mod with a different hook. Named in
the log as the mod loads if you have changed it.

### Companion mods

| | 1.7.10 | 1.12.2 |
|---|---|---|
| Waila / Hwyla | Waila | Hwyla, same package and interfaces |
| BetterQuesting quest chapter | verified | **format unverified on this version** |
| Amazing Trophies definitions | verified | **unverified; the mod may not exist here** |

Both integrations work by writing those mods' own JSON into their own config folders. The mod links
against neither, so both cost exactly nothing when absent, and a file written is offered rather
than accepted. On 1.12.2 everything around them is confirmed — the folders, the fingerprint of what
was written, the guards that make each do nothing when its mod is absent — but nobody has checked
either format against the 1.12.2 versions of those two mods.

### Mixins

| | 1.7.10 | 1.12.2 |
|---|---|---|
| Count | 3 client | 1 common, 1 client |
| On failing to bind | fails quietly | **refuses to start** (`require = 1`) |

The editions disagree here on purpose, and 1.12.2 has the better answer. Letting a mixin fail
quietly means losing the behaviour *silently*, and a feature nobody can see has failed is worse than
one that says so at startup.

1.12.2 needs three fewer mixins than 1.7.10's wear pipeline, because Forge there lets a sprite
declare dependencies, posts a collision event with the boxes about to be returned, and takes an
`IBlockColor` per position.

### Upgrading a world

Numeric block ids do not exist on 1.12.2, so the whole of 1.7.10's id-table machinery — the
missing-mapping question, the retirement of the four old per-material tampers, the id blocking —
has no counterpart there. Settings files carry forward on both, including keys only one version can
act on.

## What is the same

Worth stating, since the list above is all differences: wear accumulation and healing, every cost
curve, physical sinking, wearing through into other surfaces, slabs and stairs, weather, snow,
bone meal, what a route does to what is standing in it, surface families and detection, made
ground, all three tampers, reinforcement, warding, path light, the Golem of Ways and its eleven
upgrades, both draughts, all four guide books, the enchanted books, world-gen loot, spawn grants,
every command, the config screen, the wear table, the wear editor, presets, snapshots, server
rules, and the settings file itself.
