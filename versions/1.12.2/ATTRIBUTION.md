# Attribution and Licensing

## Original work

**The Roads More Travelled (TRMT)**
Copyright © 2026 **milkucha**
Source: <https://github.com/milkucha/trmt>
CurseForge: <https://www.curseforge.com/minecraft/mc-mods/the-roads-more-travelled>
Licensed under **Creative Commons Attribution-NonCommercial 4.0 International
(CC BY-NC 4.0)** — full text in `LICENSE_trmt`.

## This work

**TRMT Reimagined**, by *Xep*, is an unofficial, non-commercial **adaptation** of the above:
rebuilt from Fabric 1.20.1 to Minecraft 1.7.10 / Forge, targeting the GT: New Horizons
modpack, and then carried from that to Minecraft 1.12.2 / Forge. This repository is the
1.12.2 edition. It is not produced, endorsed, or supported by milkucha.

It was previously distributed as **TRMT-GTNH**. Only the display name changed; the mod id,
block names, save data and asset domain are all unchanged, so a world made with either one
loads with the other.

### What was modified

Kept up to date deliberately rather than as a courtesy: obligation 4 below asks for an
indication of prior modifications, and a list that stops a year short of the code is not one.

**The architecture**

- Ported from Fabric 1.20.1 to Forge 1.7.10
- Re-architected so erosion is stored as server-side data and rendered client-side only,
  rather than as blocks written into the world
- Persistence moved from a single world-level file to per-chunk NBT
- Healing made lazy: applied from the absolute world clock on chunk load, so unloaded chunks
  still recover, and held still while nobody is connected
- Wear filed by the block itself rather than by the id it carries, because Forge renumbers every
  modded block when a world loads and again when a client joins a server - so the surface table,
  the generated wear pictures and the record of what is drawn see-through are rebuilt when the ids
  move rather than reading another block's
- The server made the authority on which blocks wear: it sends a fingerprint of its own surface
  table with its rules, and the table itself where a client's differs, applied for the session only
  and handed back on disconnect, so no visit can write over what a player chose
- A run's whole shape put on the wire alongside it - the depth of a gradation, each family's wear
  ceiling, the successor lists that decide what a road wears through into, and the switches that
  gate them - because two machines holding different figures build different runs out of identical
  records
- A connected player's subscription reconciled against the rules whenever they change, rather than
  being decided once at the moment of joining
- Updates addressed to exactly the players the server has sent that chunk to, and ground the server
  writes over repainted rather than left as it was first drawn
- The seven classes holding the erosion model - what a record is, how it packs into sixteen bits,
  what a chain is and how deep it goes - kept free of every reference to Minecraft, enforced by a
  test that reads the source, so the model can be built for another version of the game without
  starting again

**The wear model**

- Wear expanded to eighty gradations, with physical sinking to half a block derived on both
  sides from the wear stage, and collision to match behind a mixin
- Surface families extended beyond grass, dirt and sand to gravel, stone, cobblestone, snow,
  ice, netherrack and end stone, with automatic whole-pack detection by block class, material
  and name
- Surfaces made to wear through into other surfaces as they sink, so a stone road shows cobble,
  then gravel, then the earth it was laid on
- Each family given its own recovery time outright, from snow at six in-game days to end stone
  at four hundred, rather than deriving it from what the family cost to wear
- Per-family cost curves added, so the price of a gradation changes along a run - turf tears at
  a touch, snow packs, ice glazes, dressed stone stays flat - normalised so a run's total never
  moves
- Weather linked to recovery: rain as a discount for the earthy families, and an opt-in gate
  that makes a surface mend only while precipitation is falling on it
- Snow lying on ground made to take the traffic, layer by layer, before the ground beneath marks
- Slabs, stairs and Chisel layered blocks given wear in their own shapes
- Plants, ground cover and leaves given wear of their own: a tally kept at the plant's own position
  that fades at healing's own rate, so a route crossed now and then recovers while a busy one breaks
  through. Both trampling switches ship off, since both destroy real blocks
- Mending priced per kind of ground rather than per patch, with each square paying from material it
  would itself take; the chunk tamper charging by the gradation rather than by the square; and
  mending that costs nothing earning no experience
- A block's resistance carried into every threshold written by healing, mending and restoring, not
  only by the step that wears it, so protected ground does not wear back faster than the ground
  beside it
- Ground held rather than destroyed where destroying it would take something with it - under a plant
  standing in it, or at a wear ceiling set below the whole run
- A ghost made to answer for the block it covers when the game works out how fast it breaks, so
  ground a pack gates behind a better tool breaks at the speed the server will agree to

**The textures**

- Block textures derived from the original art by rotation, applied to the generated wear sprites
  themselves: on 1.7.10 because a model cannot be rotated, and here because there is no authored
  model to rotate - a worn square's picture is composed per position from the covered block's pixels
- The authored art decomposed into a reusable per-pixel coverage sequence and re-applied to each
  block's own textures, so modded surfaces wear in their own colours
- Eleven procedural wear looks added, worked entirely from the block's own pixels, for surfaces
  nobody drew art for. `polish` and `sand` are retired names from an earlier set and are still
  read so that no config file breaks
- The wear atlas planned into the room actually measured, with every sprite priced at the size it is
  stitched at rather than at an assumed edge, and each stitch checked against the plan afterwards
- Each sprite composed once, at the edge its plan priced, with the border anisotropic filtering
  wraps round a face cropped away rather than drawn into the block
- A budget for a moving inner layer that counts everything such a layer keeps - a shell per worn
  picture, the still picture with its mip levels, the frames and any scaled copy of them - rather
  than the frames alone, and a filter restored after each upload so one moving layer cannot switch
  mipmapping off for the whole atlas

**What was added that the original has no equivalent of**

- A tool family: graded hand tampers read from config rather than hard-coded, a chunk tamper, and
  the Wayfarer's Tamper
- Three tamper enchantments - reinforcement, spawn ward and path light - each storing its state on
  the same compact per-position record
- The Golem of Ways, which holds a stretch of ground at a chosen wear level, takes eleven upgrades -
  nine that change one thing each and two that carry the whole set - and will eat reinforcing
  material to reinforce ground
- A Draught of the Heavy Foot, the opposite of the Potion of Lightness, added on vanilla's own
  terms
- Per-position pinning, bone-meal repair as a blotchy random patch that costs a block of each kind
  of ground it mends, resistant blocks, explosion scouring and fall impacts
- Four in-game guide books with their own reading screen, sixteen achievements, and quest and
  trophy definitions written as BetterQuesting's and Amazing Trophies' own data files rather than
  linked against either
- World-gen loot and optional first-join items
- The `/trmt` command set, including `showcase` and `demonstrate`, which builds a complete exhibit
  of every detected surface, look and golem upgrade for judging a change at a glance
- An in-game config screen, five preset choosers, and a wear table that draws all eleven looks on
  the player's own blocks before they choose one
- Map colouring that travels toward what the ground is becoming - answered once, per position, so
  the vanilla map item and every minimap read the same answer. On 1.7.10 this takes a reflective
  integration against JourneyMap's internals and another against Xaero's, because there a block is
  asked its colour with nothing but a metadata and cannot tell which square is being asked about;
  1.12.2 hands the position in, so neither integration exists in this edition. The optional
  desire-path highlight and the wear darkening are 1.7.10 only: both need an RGB value, and the
  answer every 1.12.2 map reads is one of sixty-four fixed palette entries
- A correction for modded turf, whose map colour is green before a biome tint is applied to it, so a
  path through it stops reading as a bright stripe rather than as worn ground
- A wear editor that previews the whole run it is about to publish, and a mobs list that stops a mob
  wearing the ground by writing it down at nought, which a wildcard line cannot override, rather than
  by removing its name
- Automation allowed to put into a golem the ground it mends with and nothing else, and to take
  nothing out
- The Golem of Ways priced the chunk tamper's way, so a golem carrying one is not charged eight times
  what the same tool costs in a player's hand

**Removals and substitutions**

- `Blocks.DIRT_PATH` and `Blocks.COARSE_DIRT` references replaced by registry names, listing
  Et Futurum Requiem's equivalents alongside vanilla's
- Brush-based sand recovery removed (no brush item exists on either of the versions this was
  rebuilt for)
- Potion of Lightness carried across with its ingredients intact - an awkward potion and a feather -
  but made at a bench rather than brewed in a stand. On 1.7.10 because there is no brewing API and
  vanilla decides a stand's output from four bits of a damage value with only three patterns
  unclaimed across a whole pack; in the 1.12.2 edition because the two editions are meant to be the
  same mod, and 1.12.2's own brewing registry would have made this the one recipe that differed
  between them
- Renamed from TRMT-GTNH to TRMT Reimagined (display name only)
- The four per-material tampers retired in favour of three items, each carrying its grade in its own
  stack data. A save that names a retired one lets it go and blocks its id, rather than the game
  refusing to open the world
- The Draught of the Heavy Foot and the Potion of Lightness made to cancel one another, neither
  removed and neither winning outright, since one silently beating the other left a player with no
  way to tell why

Textures under `src/main/resources/assets/trmtgtnh/textures/blocks/` are derived from
milkucha's original art and remain under CC BY-NC 4.0.

## Obligations under CC BY-NC 4.0

Section 2(a)(1)(b) of the license grants the right to produce and share Adapted Material
for NonCommercial purposes. Exercising that right requires all of the following:

1. **Attribute milkucha** as the creator of the original work.
2. **Retain the copyright notice**, the license notice, and the warranty disclaimer.
3. **Link the original** — <https://github.com/milkucha/trmt>.
4. **State that this is modified**, and keep an indication of prior modifications.
5. **Include the license text** (`LICENSE_trmt`) or a link to it.
6. **Non-commercial only.** No sale, no paid distribution, no monetised hosting, no
   placing it behind payment of any kind.
7. **No downstream restrictions.** Do not apply terms or technical measures that would
   prevent recipients from exercising the same rights.

The build copies `LICENSE.md`, `LICENSE_trmt` and this file into the jar from the root of the
repository, so what ships is what is written here rather than a second copy kept in step by
remembering to. Ship all three inside any distributed jar, and reproduce the
attribution on any download page. `LICENSE.md` is the plain-language statement of all of the
above; `LICENSE_trmt` is the licence text itself, retained unmodified as upstream supplied it.

## Courtesy

Not required by the license, but worth doing: tell milkucha the backport exists. Authors
generally like knowing, and it opens the door to upstreaming fixes.

## Other components

- Build scaffolding derives from **CleanroomMC/ForgeDevEnv**, the 1.12.2 workspace template,
  used under the **MIT License** — full text in `LICENSE_forgedevenv`, retained as that
  licence requires. The 1.7.10 edition's scaffolding instead derives from
  **GTNewHorizons/ExampleMod1.7.10**; see that project for its own licence.
- Et Futurum Requiem's `etfuturum:grass_path` and `etfuturum:farmland` appear as registry
  names in default block lists, and are skipped when nothing provides them. No Et Futurum
  code is referenced, copied or redistributed here.
- Every other integration - Waila, JourneyMap, Xaero's Minimap, Angelica/Iris, Chisel,
  GregTech, Amazing Trophies, BetterQuesting - is behind a check that it is present and none of
  their code is redistributed. JourneyMap and Xaero's Minimap are reached entirely by reflection.
- No Mojang assets are redistributed. The vanilla dirt texture used for grass compositing
  is read from the running game at texture-stitch time, never shipped.
