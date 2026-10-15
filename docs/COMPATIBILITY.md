# Where the editions differ

TRMT: Reimagined is one mod with one version number. Every edition does the same thing by the same
numbers and reads the same settings file, and thirty-five classes are shared between them byte for
byte, with a test in each edition's development tree that fails if the copies drift.

This page is everything that is *not* the same, in one place. The manual says each of these in the
section it belongs to as well; this is the list for somebody deciding which version to play, or
moving a pack from one to the other.

## Supported versions

| Minecraft | Loader | Status | Needs |
|---|---|---|---|
| **1.7.10** | Forge 10.13.4.1614+ | released | [UniMixins](https://github.com/LegacyModdingMC/UniMixins), or the older [GTNHMixins](https://github.com/GTNH-Museum/GTNHMixins) |
| **1.12.2** | Forge 14.23.5.2847+ | released | [MixinBooter](https://github.com/CleanroomMC/MixinBooter) |
| **1.16.5** | Forge 36.2.34+ **or** Fabric | released | [Cloth Config](https://github.com/shedaniel/cloth-config); on Fabric also [Fabric API](https://github.com/FabricMC/fabric-api), and [Mod Menu](https://github.com/TerraformersMC/ModMenu) to reach the settings screen |
| **1.20.1** | Forge 47+ **or** Fabric | beta | [Cloth Config](https://github.com/shedaniel/cloth-config) 11.0.99+; on Fabric also [Fabric API](https://github.com/FabricMC/fabric-api) 0.86.0+, and [Mod Menu](https://github.com/TerraformersMC/ModMenu) to reach the settings screen |
| **1.21.1** | NeoForge 21.1+, Forge 52+ **or** Fabric | alpha | [Cloth Config](https://github.com/shedaniel/cloth-config) 15.0.140+; on Fabric also [Fabric API](https://github.com/FabricMC/fabric-api) 0.116.17+, and [Mod Menu](https://github.com/TerraformersMC/ModMenu) to reach the settings screen |

All five run on plain Forge - or plain NeoForge, or plain Fabric - with nothing else installed. Every other
integration is behind a mod-loaded check.

1.16.5 and 1.20.1 are each one codebase and two jars, and 1.21.1 is one codebase and three. Take the one that
matches your loader. 1.20.1 is a beta, carried from 1.16.5 and held to 1.7.10 rule for rule. 1.21.1 is an alpha: the
first build for that version, carried from 1.20.1 and held to 1.7.10 the same way, and it runs on Java 21, as
Minecraft 1.21.1 does. In every table below 1.20.1 answers as 1.16.5 does, and 1.21.1 as 1.20.1 does, unless the
table says otherwise.

## The differences, and why

### Maps

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Worn ground colored per position | yes | yes | yes | yes | yes |
| How | reflection into JourneyMap's and Xaero's internals | one block override, no map mod named | one block override, no map mod named | one block override, no map mod named | one block override, no map mod named |
| A road darkens as it wears | by any fraction | to **one** darker color | to **one** darker color | to **one** darker color | to **one** darker color |
| `surfaces.mapTracksWear` | switchable | switchable | switchable | switchable | switchable |
| `surfaces.mapWearDarkening` | the fraction itself | picks *which* darker color | picks *which* darker color | picks *which* darker color | picks *which* darker color |
| `client.desirePathHighlight` | works | does nothing | does nothing | does nothing | does nothing |
| `client.mapWearThroughTint` | works | does nothing | does nothing | does nothing | does nothing |

On 1.7.10 a block is asked its color with nothing but a metadata value, so a worn square cannot
tell which position is being asked about. Reaching into those two mods' own color lookups is the
only way to answer per position - and because it hands them a real RGB value, that edition can
darken a road by any fraction and pull a route toward a violet that is not a material at all.

From 1.12.2 on the position is handed in. One override answers every map that reads the world, so
both integrations disappear and there is nothing to go stale when a map mod changes its internals.
The cost is that the answer is one of vanilla's sixty-four fixed palette entries: there are no
fractions, so a worn square reports the nearest entry to its own color darkened by
`surfaces.mapWearDarkening`, and does not darken further as it wears. Grass at `#7fb238` is drawn as
`#392923`. **A road is visible on the map; how worn it is, is not.** Where a pack's ground has
nothing darker in the palette worth picking it keeps its color, and the log says so once, by name.

On 1.20.1 and 1.21.1 that one answer has been read back from three more maps, each opened over worn ground:
[Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map) on every loader,
FTB Chunks ([Forge and NeoForge](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge),
[Fabric](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-fabric)) on 1.20.1 and on 1.21.1 NeoForge and Fabric,
and [VoxelMap Updated](https://modrinth.com/mod/voxelmap-updated) on Fabric. On 1.21.1, Xaero's Minimap and JourneyMap
draw the test's worn lawns in exactly 1.20.1's colors.

The two settings that still do nothing are both fractions of a color, which is the thing this
mechanism has not got. They stay in the file so a pack can move between versions and find its edits
where it left them, and the mod names any of them you have changed, once, as it loads.

**The vanilla map item never shows wear, in any edition.** It is drawn from the server's own blocks,
and no worn square exists there - this mod paints them into each client's copy of the world and
writes nothing into the save. Minimaps that read the client's world show the path. `/trmt mapcolor`
reports both answers for the square you are standing on.

### Progression

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Sixteen entries | achievements, on their own page | advancements | advancements | advancements | advancements |
| Translation keys | the same | the same | the same | the same | the same |
| Conditional registration | per switch | read where the advancement is granted | read where the advancement is granted | read where the advancement is granted | read where the advancement is granted |

1.12 removed the achievement system. The same sixteen, the same lang keys, so one translation serves
every edition. A JSON file cannot be registered conditionally, so every switch an achievement answered
to is read at the moment of granting instead.

### Ghost blocks

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Cosmetic blocks registered | **66** | **1** | **2** | **2** | **2** |

Not sixty-six pictures - one block class per shape and per rendering path, because on 1.7.10 a
block's appearance is a metadata value and an icon per side. Later versions make appearance a
blockstate and a model handed the position, so one class carries every shape and every gradation -
and on 1.16.5, 1.20.1 and 1.21.1 a second carries worn stairs, whose shape is the stair's own.

This matters in one visible way: deleting the jar makes Forge show its *"found ID mismatches"*
screen once, with sixty-six names to report on 1.7.10 and one or two on the others. Your world is intact
either way - none of those blocks is ever placed in the saved world.

### Materials

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| How a material list is read | the ore dictionary | the ore dictionary | item tags | item tags | item tags, under `c:` |

Every list of materials in the settings is written in ore-dictionary names - `ingotIron`,
`gemDiamond` - and stays that way on every edition, so a settings file carries across. The ore
dictionary is gone from 1.13 on, so 1.16.5 and 1.20.1 translate each name to the tag it means: `ingotIron`
becomes `forge:ingots/iron`. A pack whose material has no conventional tag can write the tag name
itself in the setting; anything with a colon or a slash in it is taken as written.

Forge ships those tags. Fabric does not, so the Fabric jar ships the vanilla ones itself.

On 1.21.1 the same names are translated to the `c:` convention on all three loaders - `ingotIron` becomes
`c:ingots/iron` - because NeoForge and Forge 52 took up Fabric's convention at 1.21 and the old `forge:` folders name
nothing there. NeoForge ships those tags, Forge 52 ships them beside its own, and Fabric API's convention tags are
`c:`, so no 1.21.1 jar ships tags of its own.

### Shaders

| | 1.7.10 | 1.12.2 | 1.16.5 Forge | 1.16.5 Fabric | 1.20.1 Forge | 1.20.1 Fabric | 1.21.1 NeoForge | 1.21.1 Forge | 1.21.1 Fabric |
|---|---|---|---|---|---|---|---|---|---|
| Worn ground keeps the covered block's shader material | Angelica | OptiFine | Oculus, OptiFine | Iris, Canvas, OptiFabric | Oculus, with Embeddium; OptiFine | Iris, with Sodium; OptiFabric | Iris, with Sodium | OptiFine | Iris, with Sodium |

`client.inheritShaderMaterial` is written for all three and reachable on all three. 1.7.10 calls
`Iris.setShaderMaterialOverride`, a pair of methods **Angelica** added for this. The later editions
reach what that pair is a convenience for, one step further in: the per-block holder a shader mod's
own vertex writer reads. **Oculus** provides it on 1.16.5 Forge and **Iris** provides it on 1.16.5
Fabric - Oculus being a fork of Iris that kept the upstream packages, so one seam answers for both. On 1.20.1
both keep that holder under Iris's newer package, and it is asked for there. On 1.21.1 no Oculus is published, and
Iris 1.8 has moved that holder again and changed it: it takes Iris's own id for a block, so the claim is the covered
block's id from Iris's own map. Iris, with Sodium, carries it on NeoForge and on Fabric.

**OptiFine is not of the Iris family and has a holder of its own.** It works out a block's shader id
from the pack's `block.properties` as the block's model starts and pushes it onto a stack the chunk's
buffer carries; a ghost's entry there is written over, in place, with the block it stands in for -
on 1.12.2, where no Oculus is published, and on 1.16.5 and 1.20.1 Forge. **Canvas** is asked
another way again: a worn square takes the covered block's FREX material in its own pass.

**Under OptiFabric** the claim is made from the other side. OptiFabric defines OptiFine's classes after
every mod's hooks have been prepared, so none can bind to them, and Fabric's own renderer draws a worn
square there, so OptiFine never pushes an entry for it at all: the mod's model pushes the claim onto that
stack itself, around the quads it hands Fabric's renderer, and takes it off again after - on 1.16.5 and on
1.20.1, where OptiFine HD U I6 runs through OptiFabric 1.14.3 on Fabric Loader 0.14.25, the newest loader it starts
on.

None of this does anything without a shader pack loaded. Every combination in the table but Canvas
and 1.20.1's has been photographed under Complementary Reimagined and Sildurs Vibrant, beside the same
renderer without a pack and again after the pack was turned off mid-session; Canvas draws through its
own pipeline and was photographed under that. 1.20.1's four were photographed under Complementary Reimagined, beside 1.16.5's same renderers under it - OptiFine
and OptiFabric among them, each saying in its log that the claim reached OptiFine's stack.
1.21.1's Iris, with Sodium on NeoForge and on Fabric, was run under a shader pack, and each run's log counted the
claims that landed; OptiFine HD U J1 on Forge was run under Complementary Reimagined and said in its log that the
claim reached OptiFine's stack.

**On 1.21.1, Embeddium on NeoForge draws worn ground without a shader loader beside it**, photographed beside the
1.7.10 edition. OptiFine HD U J1 runs on Forge 52.0.16, the Forge it names - on Forge 52.1 it stops a server the
moment a chest is filled, in OptiFine's own code.

### Companion mods

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Block tooltip | Waila | Hwyla | Jade on Forge, WTHIT on Fabric | Jade or WTHIT, on either loader, and The One Probe on Forge | Jade on NeoForge and Fabric, WTHIT on all three, The One Probe on NeoForge |
| BetterQuesting quest chapter | verified | verified, merged into `DefaultQuests.json`, with vanilla stand-ins for rewards the pack lacks | no such mod here | FTB Quests alone, by decision: its one build reads no default quests, so none is written | FTB Quests alone, as on 1.20.1 |
| FTB Quests quest chapter | not written | not written | not written | verified on both loaders, written as its own chapter file, its rewards paid in vanilla stand-ins | verified on NeoForge and Fabric, written as its own chapter file, its rewards paid in vanilla stand-ins |
| Amazing Trophies definitions | verified | unverified; the mod may not exist | no such mod here | no such mod here | no such mod here |
| Chisel's successors | - | - | - | Rechiseled, Chisel Reborn and Chipped: their blocks wear and appear in the Wear Table | Rechiseled on all three; Chisel Reborn and Chipped, which have no Forge build, on NeoForge and Fabric: their blocks wear and appear in the Wear Table |

All three tooltip mods descend from WAILA and keep the same `mcp.mobius.waila.api` package, so one
integration covers them - 1.16.5, 1.20.1 and 1.21.1 are written against the newer shape of that API rather than
carried. On 1.20.1 and 1.21.1, Jade and WTHIT name a worn square for the block it covers, with that block's mod, as
Waila does on 1.7.10, and every jar of those two versions carries both plugins.

The quest and trophy integrations work by writing those mods' own files into their own config
folders - JSON for BetterQuesting and the trophies, FTB Quests' own SNBT on 1.20.1 and 1.21.1. This mod links against
neither, so both cost exactly nothing when absent, and a file written is offered rather than accepted.

### Mixins

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Count | 3 common, 10 client | 1 common, 7 client | 4 shared, 33 more on Forge, 59 on Fabric | 5 shared, 32 more on Forge, 61 on Fabric | 9 shared, 37 more on NeoForge, 34 on Forge, 59 on Fabric |

A mixin that cannot bind - because something else in the pack has moved what it attaches to - costs
the behaviour it carries and not the launch, in every edition. Nothing any of them hooks can damage
a world, so refusing to start would be aimed at a player who did not choose the conflict and cannot
fix it.

The Fabric number is the high one, and not because that jar does more. Forge patches vanilla
directly and offers events for several of these questions; where it does, Fabric needs a mixin. And
a mixin that names a method has that name written into a refmap in one loader's names, which the
others refuse - so a hook every loader needs exists once for each, with identical bodies. 1.21.1's shared module
holds more of them, because Forge 52 runs Mojang's own names, so a shared hook may name a vanilla member there, which
on 1.20.1 it could not.

### Settings screen

| | 1.7.10 | 1.12.2 | 1.16.5 | 1.20.1 | 1.21.1 |
|---|---|---|---|---|---|
| Reached from | Forge's mod list | Forge's mod list | Forge's mod list, or Mod Menu on Fabric | Forge's mod list, or Mod Menu on Fabric | NeoForge's or Forge's mod list, or Mod Menu on Fabric |
| Built on | Forge's config screen | Forge's config screen | Cloth Config | Cloth Config | Cloth Config |

Forge's own config screen was removed after 1.12.2 and vanilla has never had one, so 1.16.5, 1.20.1 and 1.21.1 use
Cloth Config - the one dependency this mod takes that is not a loader. Fabric has no mod list with a
Config button at all, which is what Mod Menu is for; without it the settings file and `/trmt reload`
are the way in.

### Upgrading a world

Numeric block ids do not exist from 1.12.2 on, so the whole of 1.7.10's id-table machinery - the
missing-mapping question, the retirement of the four old per-material tampers, the id blocking - has
no counterpart there. Settings files carry forward on all five, including keys only one version can
act on. **A 1.16.5 world opened on 1.20.1 keeps its wear**: the game's own upgrade carries each chunk's
record, and the first sweep heals it on from where it was. **A 1.20.1 world opened on 1.21.1 keeps its wear the same
way**, and a Golem of Ways keeps what it carries and the upgrade fitted to it - its stacks are put through the game's
own item upgrade before they are read.

## What is the same

Worth stating, since the list above is all differences: wear accumulation and healing, every cost
curve, physical sinking, wearing through into other surfaces, slabs and stairs, weather, snow, bone
meal, what a route does to what is standing in it, surface families and detection, made ground, all
three tampers, reinforcement, warding, path light, the Golem of Ways and its eleven upgrades, both
draughts, all four guide books, the enchanted books, world-gen loot, spawn grants, every command,
the config screen, the wear table, the wear editor, presets, snapshots, server rules, and the
settings file itself.
