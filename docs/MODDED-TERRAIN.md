# Modded terrain

TRMT: Reimagined does not keep a list of which blocks are ground. It reads every block in the game as it starts and
decides from what each one is - its class, its material, its shape - which family it wears in, if any. This page says
what that decision rests on, how it was checked against real modpacks, what was corrected, and where it still stops.
The manual's [Which blocks erode](../README.md#which-blocks-erode) and
[Surface families](../README.md#surface-families) have the settings and the numbers.

## Three layers, and who wins

1. **Detection**, the rules above, the same on client and server.
2. **Data files**, which name a block's family, shape, look or how a mob wears it. This mod ships them for the blocks
   detection gets wrong, and any mod, resource pack or data pack can ship its own (the manual's
   [For mod and pack authors](../README.md#for-mod-and-pack-authors)). Data beats detection.
3. **Your settings**: each family's block list, `surfaces.exclude`, a family switched off. Your settings beat data.

Detection writes what it finds into each family's block list, so every settings file holds lines nobody chose. **A line
there that says only what detection would have said counts as detection's**, and a data file can correct it, so a
correction shipped in a later release reaches a world that has already run. A line that disagrees with detection was
written by somebody on purpose, and still wins over everything.

`/trmt surfaces` lists what was found, and `/trmt data <block>` says which file, if any, decided a block.

## The families

Sixteen. Fourteen wear: grass, dirt, sand, gravel, cobble, stone, snow, ice, nether, end, flesh, taint, sculk and cloud.
Leaves and vegetation are only trampled.

The last four are found only where something places them:

| Family | What wears in it | How it wears |
|---|---|---|
| flesh | Biomes O' Plenty's flesh, RLCraft's parasite stain, Tinkers' Construct's congealed slime | sand's prices, packing down; sinks as itself to half a block; back in 37.5 in-game days |
| taint | Thaumcraft's crusted taint | cobblestone's prices; sinks as itself to half a block; back in 125 days |
| sculk | vanilla sculk from 1.19, Et Futurum Requiem's on 1.7.10, Deeper and Darker's | sand's prices, packing down; a deep rut shows the stone beneath; back in 100 days |
| cloud | the Twilight Forest's fluffy cloud | snow's prices, packing down four pixels; back in 6 days |

Among the older families: **mycelium** wears as grass and **soul sand** as sand, nether wart keeping its square, and
from 1.20.1 vanilla **mud** wears as dirt. **A slab or stair** wears in its full block's family, and stays unworn where
its full block does.

## How it was checked

Each edition was run in large packs, and the ground those packs generate was read back across their biomes and
dimensions - Galacticraft's and GalaxySpace's planets and the other mods' own worlds included - and every block found
as natural ground was ruled on: placed in its family, or kept out where it is not ground a path should wear, such as an
ore, a structure's block or a block that keeps state of its own. Cave floors were read and placed, and left out of the
share. Every correction is a data file, never a change to the rules, so a pack that holds none of the blocks a file
names is untouched by it.

| Edition | Pack | Natural blocks placed in a family |
|---|---|---|
| 1.7.10 | GT New Horizons 2.8.4 | 127 |
| 1.7.10 | FTB Infinity Evolved 3.1.0 | 78 |
| 1.12.2 | RLCraft 2.9.3 | 108 |
| 1.12.2 | Enigmatica 2 Expert 1.93a | 78 |
| 1.16.5 Forge | All the Mods 6 1.9.2, its TerraForged and default worlds | 159 |
| 1.16.5 Fabric | All of Fabric 3 2.8.8 | 138 |
| 1.20.1 Forge | All the Mods 9 1.1.1 | 162 |
| 1.20.1 Fabric | Prominence II 4.1.6 | 151 |
| 1.21.1 NeoForge | All the Mods 10 8.5 | 173 |
| 1.21.1 Fabric | Cobblemon Official 1.8.4 | 52 |
| 26.1.2 Fabric | a set of the larger terrain mods for 26.1 | 164 |
| 26.2 Fabric | a set of the larger terrain mods for 26.2 | 124 |
| 26.3 Fabric | a set of the larger terrain mods for 26.3 | 126 |

A plain game of each edition was read the same way. In every one, no block found as natural ground is left in the wrong
family or unruled.

Some of what was corrected:

- GalaxySpace's planets and moons, and Galacticraft's Moon and Mars, wear as stone, and Barnarda C's dirt as dirt.
- Quark's basalt wears as nether; Ice and Fire's charred grass as grass; Enigmatica 2 Expert's dried sand
  as sand; Biomes O' Plenty's nether grass as nether; EvilCraft's infested nether soul sand as sand.
- A mod's rock named after itself stays stone, and Better End's and Regions Unexplored's leaf-tagged blocks are leaves.
- Oh The Biomes We've Gone, Regions Unexplored, Nature's Spirit, Terrestria, Grass Overhaul and TerraFirmaCraft each have
  their soils, muds, sods, ash and rock named.
- Deeper and Darker's sculk jaw, which bites, is never worn in any edition.

## Where it stops

- **A settings file from an earlier release** still holds `minecraft:mycelium` and `minecraft:soul_sand` in
  `surfaces.exclude`, and that line wins: take both out for them to wear in an existing world.
- **Worn soul sand and mud** are walked on at their worn height, which is above the real block's, and at full speed,
  while a mob on the real block stays slowed; Soul Speed does not apply on a worn square. On 1.7.10 and 1.12.2, nether
  wart above a worn square may drop on your client.
- **A map mod may draw worn mycelium green**, since a worn square takes its map color from grass.
- **Fluffy cloud** in a 1.7.10 or 1.16.5 world was worn as ice before; that wear is lost once, as the block moves to
  cloud.
- **Galacticraft Amun-Ra's red grass** is on the shipped exclusion list and does not wear, even on its own world.
- **Slabs and stairs whose full block cannot be found by name** keep what detection gives them: Macaw's Roofs on 1.16.5,
  Rechiseled's connecting slabs, Quark's vertical slabs, which the game does not call slabs, and a few dozen more.
- **Stone bricks, planks, bricks, concrete, wool and other made blocks** are not natural ground, and were not ruled on.
- **On 1.12.2 with StellarCore**, its ParallelTextureLoad option, off as StellarCore ships and in Enigmatica 2 Expert,
  is not supported.
