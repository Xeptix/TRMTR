# Where the editions differ

TRMT Reimagined is one mod with one version number. Every edition does the same thing by the same
numbers and reads the same settings file, and twenty-eight classes are shared between them byte for
byte, with a test in each that fails the build if the copies drift.

This page is everything that is *not* the same, in one place. The manual says each of these in the
section it belongs to as well; this is the list for somebody deciding which version to play, or
moving a pack from one to the other.

## Supported versions

| Minecraft | Loader | Status | Needs |
|---|---|---|---|
| **1.7.10** | Forge 10.13.4.1614+ | released | [UniMixins](https://github.com/LegacyModdingMC/UniMixins), or the older [GTNHMixins](https://www.curseforge.com/minecraft/mc-mods/gtnhmixins) |
| **1.12.2** | Forge 14.23.5.2847+ | released | [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) |
| **1.16.5** | Forge 36.2.34+ **or** Fabric | released | [Cloth Config](https://www.curseforge.com/minecraft/mc-mods/cloth-config); on Fabric also Fabric API, and Mod Menu to reach the settings screen |

All three run on plain Forge — or plain Fabric — with nothing else installed. Every other
integration is behind a mod-loaded check.

1.16.5 is one codebase and two jars. Take the one that matches your loader.

## The differences, and why

### Maps

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Worn ground coloured per position | yes | yes | yes |
| How | reflection into JourneyMap's and Xaero's internals | one block override, no map mod named | one block override, no map mod named |
| A road darkens as it wears | by any fraction | to **one** darker colour | to **one** darker colour |
| `surfaces.mapTracksWear` | switchable | switchable | switchable |
| `surfaces.mapWearDarkening` | the fraction itself | picks *which* darker colour | picks *which* darker colour |
| `client.desirePathHighlight` | works | does nothing | does nothing |
| `client.mapWearThroughTint` | works | does nothing | does nothing |

On 1.7.10 a block is asked its colour with nothing but a metadata value, so a worn square cannot
tell which position is being asked about. Reaching into those two mods' own colour lookups is the
only way to answer per position — and because it hands them a real RGB value, that edition can
darken a road by any fraction and pull a route toward a violet that is not a material at all.

From 1.12.2 on the position is handed in. One override answers every map that reads the world, so
both integrations disappear and there is nothing to go stale when a map mod changes its internals.
The cost is that the answer is one of vanilla's sixty-four fixed palette entries: there are no
fractions, so a worn square reports the nearest entry to its own colour darkened by
`surfaces.mapWearDarkening`, and does not darken further as it wears. Grass at `#7fb238` is drawn as
`#392923`. **A road is visible on the map; how worn it is, is not.** Where a pack's ground has
nothing darker in the palette worth picking it keeps its colour, and the log says so once, by name.

The two settings that still do nothing are both fractions of a colour, which is the thing this
mechanism has not got. They stay in the file so a pack can move between versions and find its edits
where it left them, and the mod names any of them you have changed, once, as it loads.

**The vanilla map item never shows wear, in any edition.** It is drawn from the server's own blocks,
and no worn square exists there — this mod paints them into each client's copy of the world and
writes nothing into the save. Minimaps that read the client's world show the path. `/trmt mapcolour`
reports both answers for the square you are standing on.

### Progression

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Sixteen entries | achievements, on their own page | advancements | advancements |
| Translation keys | the same | the same | the same |
| Conditional registration | per switch | read where the advancement is granted | read where the advancement is granted |

1.12 removed the achievement system. The same sixteen, the same lang keys, so one translation serves
all three. A JSON file cannot be registered conditionally, so every switch an achievement answered
to is read at the moment of granting instead.

### Ghost blocks

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Cosmetic blocks registered | **66** | **1** | **1** |

Not sixty-six pictures — one block class per shape and per rendering path, because on 1.7.10 a
block's appearance is a metadata value and an icon per side. Later versions make appearance a
blockstate and a model handed the position, so one class carries every shape and every gradation.

This matters in one visible way: deleting the jar makes Forge show its *"found ID mismatches"*
screen once, with sixty-six names to report on 1.7.10 and one on the others. Your world is intact
either way — none of those blocks is ever placed in the saved world.

### Materials

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| How a material list is read | the ore dictionary | the ore dictionary | item tags |

Every list of materials in the settings is written in ore-dictionary names — `ingotIron`,
`gemDiamond` — and stays that way on every edition, so a settings file carries across. The ore
dictionary is gone from 1.13 on, so 1.16.5 translates each name to the tag it means: `ingotIron`
becomes `forge:ingots/iron`. A pack whose material has no conventional tag can write the tag name
itself in the setting; anything with a colon or a slash in it is taken as written.

Forge ships those tags. Fabric does not, so the Fabric jar ships the vanilla ones itself.

### Shaders

`client.inheritShaderMaterial` works on all three, through a different mod and a different seam on
each. 1.7.10 calls `Iris.setShaderMaterialOverride`, a pair of methods **Angelica** added for this.
1.12.2 reaches what that pair is a convenience for, one step further in, through **Oculus**. 1.16.5
is the straightforward one, because Oculus is a 1.16.5 mod rather than a backport. None of them does
anything without a shader pack loaded.

### Companion mods

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Block tooltip | Waila | Hwyla | Jade on Forge, WTHIT on Fabric |
| BetterQuesting quest chapter | verified | format unverified on this version | no such mod here |
| Amazing Trophies definitions | verified | unverified; the mod may not exist | no such mod here |

All three tooltip mods descend from WAILA and keep the same `mcp.mobius.waila.api` package, so one
integration covers them — 1.16.5 is written against the newer shape of that API rather than carried.

The quest and trophy integrations work by writing those mods' own JSON into their own config
folders. This mod links against neither, so both cost exactly nothing when absent, and a file
written is offered rather than accepted.

### Mixins

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Count | 3 client | 1 common, 1 client | 2 shared, 7 more on Forge, 16 on Fabric |

A mixin that cannot bind — because something else in the pack has moved what it attaches to — costs
the behaviour it carries and not the launch, in every edition. Nothing any of them hooks can damage
a world, so refusing to start would be aimed at a player who did not choose the conflict and cannot
fix it.

The Fabric number is the high one, and not because that jar does more. Forge patches vanilla
directly and offers events for several of these questions; where it does, Fabric needs a mixin. And
a mixin that names a method has that name written into a refmap in one loader's names, which the
other refuses — so a hook both loaders need exists twice, with identical bodies.

### Settings screen

| | 1.7.10 | 1.12.2 | 1.16.5 |
|---|---|---|---|
| Reached from | Forge's mod list | Forge's mod list | Forge's mod list, or Mod Menu on Fabric |
| Built on | Forge's config screen | Forge's config screen | Cloth Config |

Forge's own config screen was removed after 1.12.2 and vanilla has never had one, so 1.16.5 uses
Cloth Config — the one dependency this mod takes that is not a loader. Fabric has no mod list with a
Config button at all, which is what Mod Menu is for; without it the settings file and `/trmt reload`
are the way in.

### Upgrading a world

Numeric block ids do not exist from 1.12.2 on, so the whole of 1.7.10's id-table machinery — the
missing-mapping question, the retirement of the four old per-material tampers, the id blocking — has
no counterpart there. Settings files carry forward on all three, including keys only one version can
act on.

## What is the same

Worth stating, since the list above is all differences: wear accumulation and healing, every cost
curve, physical sinking, wearing through into other surfaces, slabs and stairs, weather, snow, bone
meal, what a route does to what is standing in it, surface families and detection, made ground, all
three tampers, reinforcement, warding, path light, the Golem of Ways and its eleven upgrades, both
draughts, all four guide books, the enchanted books, world-gen loot, spawn grants, every command,
the config screen, the wear table, the wear editor, presets, snapshots, server rules, and the
settings file itself.
