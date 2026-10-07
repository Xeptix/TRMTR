# TRMT Reimagined - changelog

One version number across every edition. 0.9.218 is the same release on Minecraft 1.7.10, 1.12.2
and 1.16.5, and later releases will carry whatever editions exist then. A change in one edition
moves the number for all of them, so an edition's entry saying nothing changed is saying something true.

## 0.9.218 - 2026-10-07

**Worn grass draws correctly under OptiFine, on 1.7.10.** With OptiFine installed, the side of a worn
grass block came out an even olive green, with none of the thin green fringe along its top edge,
where plain 1.7.10 and [Angelica](https://github.com/GTNewHorizons/Angelica) draw brown earth with
that strip laid over it. OptiFine is the renderer most people on that version run, and the fault had
been in every release. It was two faults, one hidden under the other.

A grass block's sides are drawn untinted with a separately tinted fringe laid over them, and both
halves of that are decided inside the renderer, not by the block. Vanilla recognises grass by name -
the name of its top texture for the tint, the name of the side texture for the fringe. OptiFine
compares against vanilla's own block and vanilla's own texture instead, object for object. This mod
was answering the first comparison in one of the three methods a block is drawn through, so with
smooth lighting on, which is the default, the tint landed on the earth; and it was not answering the
second at all, so the earth wall of worn grass was never given its fringe. Both are answered now,
wherever OptiFine asks them - and never where Better Grass asks whether to paint a side over with
grass, so with Better Grass on a worn wall still shows its earth.

The top of a worn square keeps its wear and its biome tint, and nothing changes on any other renderer.

**Nothing else changed.** 1.12.2 and 1.16.5 draw through baked models, never reach the renderer
either fault lived in, and differ from 0.9.217 only in the version they report.

## 0.9.217 - 2026-10-06

**A third edition: Minecraft 1.16.5, on Forge and on Fabric.** One codebase, two jars, and the same
mod as the other two - the same behaviour, the same numbers, the same settings file. Everything the
1.7.10 edition does, this one does, except where the game itself has changed underneath it; every one
of those is named in [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md).

It needs [Cloth Config](https://github.com/shedaniel/cloth-config) on both loaders, [Fabric API](https://github.com/FabricMC/fabric-api) on Fabric, and
[Mod Menu](https://github.com/TerraformersMC/ModMenu) if you want to reach the settings screen there. Nothing else.

**Worn ground is drawn under Rubidium, on 1.16.5 Forge.** The 1.16.5 edition told its ghost block
which square it was drawing through one door only, and [Rubidium](https://github.com/Asek3/Rubidium)
meshes chunks itself without ever opening that door - so with it installed every worn square drew
nothing at all. Because a ghost *replaces* the ground in your client's copy of the world rather than
covering it, nothing is not a missing path: it is a rectangular pit with the sky visible through one
edge. It now answers Forge's own per-block model-data hook, which Rubidium does ask, and **no renderer
can make a hole again**: told nothing, both loaders draw the plain block, which is what the 1.12.2
edition has always done. Found by driving the game with Rubidium and Oculus installed, which no
development run has.

**Three settings show their names on 1.16.5.** `showErosion`, `overlayDistanceChunks` and
`perSurfaceTextures` carry human names, the translations shipped with the edition, and its settings
screen - which is Cloth's rather than Forge's - never looked them up. They read "Show erosion" where
the other two editions read "Show worn paths".

**A log line can no longer grow without limit.** Three lines in every edition handed a whole
collection to the log, which on a large pack is a string long enough to exhaust the heap building it;
the 1.16.5 edition died that way at startup. Each now prints a sample and says how many it stood for.

**A road is visible on a map again, on 1.12.2 and 1.16.5.** Both of those read one vanilla answer for
a block's map colour, out of sixty-four fixed palette entries, so there is no fraction to darken by -
and both had therefore given the darkening up, which left a path showing on a minimap only once it
had worn through into another material. A palette entry can be picked for being darker even though a
colour cannot be dimmed, so a worn square now reports the nearest entry to its own colour darkened by
`surfaces.mapWearDarkening`. Grass is drawn as dark earth. The road is there; how worn it is cannot be
read off the map, and the setting, the log and the manual all say so.

**Worn ground inherits its shader material on 1.12.2 too.** Under a shader pack a ghost was in
nobody's block-material table, so a worn stone road did not merely lose its polish - it lost every
property the unworn block had and fell back on whatever the pack does with a stranger. It now claims
the block it is covering while it still shows that block's surface, and the earth it has worn into
once it does not, so the shine leaves as the road breaks up rather than the moment somebody walks on
it. It needs a shader loader of the Iris family - **[Angelica](https://github.com/GTNewHorizons/Angelica)** on 1.7.10,
**[Oculus](https://github.com/Asek3/Oculus)** on 1.16.5 Forge, **[Iris](https://github.com/IrisShaders/Iris)** itself on 1.16.5 Fabric - and does nothing without
one or without a shader pack loaded. **On 1.12.2 there is no such loader published**: the seam is
there, no Oculus is released for that version, and Optifine is a different lineage with nothing to
claim. It adds nothing to what any edition requires.

**Fourteen lines of text on 1.12.2 were showing their own keys.** Twelve of them are the whole of a
golem's block tooltip, so looking at one through Hwyla read `trmtgtnh.golem.waila.idle` where it
should have said "Nothing to do here". One was the chunk tamper's settings screen, and one was the
name of a worn square itself. A missing translation is not an error and nothing logs it, so this had
been true for as long as the golem has existed on that version. A test in each edition now reads the
source for every key it asks for and insists the language file has it.

**Worn ground drew as garbage on 1.12.2 under any shader pack, and crashed the game when shaders were
switched off.** It built its quads through Forge's `UnpackedBakedQuad`, which measures its packed
array from the vertex format when it is created and fills it from that same format later - and that
format is shared and can grow. OptiFine grows it when a shader pack loads, so the array was written
past its end: the hollow drew as enormous stretched blades, and toggling shaders mid-session ended in
an `ArrayIndexOutOfBoundsException` while tesselating a worn square. Shaders off was always correct,
which is why it survived this long. The quads are now packed by hand in the layout vanilla's own
blocks use, as the 1.16.5 edition has always done, and a test holds that shape. Found by installing
OptiFine and looking.

The 1.7.10 jar is unchanged in this release. The number moves because a release is one number across
every edition.

---

### On Minecraft 1.16.5, which is new

- **Both loaders from one codebase.** A shared module holds the mod; a Forge module and a Fabric
  module hold an entry point each. The two jars are built from the same source and behave the same.
- Everything the other editions have: eighty gradations of wear, physical sinking with real
  collision, healing by elapsed world time, weather, snow, bone meal, wearing through into other
  surfaces, all three tampers, reinforcement, warding, path light, the Golem of Ways and its eleven
  upgrades, both draughts, all four guide books, the sixteen advancements, world-gen loot, every
  command, the wear table and its editor, presets and snapshots.
- **Wear textures are generated from each block's own pixels**, as everywhere else: 12,800 of them
  composed as the game loads, into a resource pack of the mod's own.
- The settings screen is built on Cloth Config, because Forge's own was removed after 1.12.2 and
  vanilla has never had one. On Fabric it is reached through Mod Menu.
- Materials are read from item tags rather than the ore dictionary, which no longer exists. The
  settings keep their ore-dictionary spelling and are translated to tags; the Fabric jar ships the
  vanilla tags itself, because nothing on that loader does.
- Advancements rather than achievements, as on 1.12.2: the same sixteen, the same translation keys.

## 0.9.216 - 2026-10-03

**First public release, on Minecraft 1.7.10 and 1.12.2.**

Ground wears into paths along the routes that are actually walked, and grows back once they are not.

Wear is kept as server-side data and painted on the client as ghost blocks. No block is ever written
into the world, so the mod can be switched off for one player, disabled across a server, or deleted
outright without a single square of terrain having changed. That decision is the oldest one here and
everything else is built on it.

Neither edition has been published anywhere before, so there is no list of changes since a previous
release to give. What follows instead is what each of them is, by area. Releases after this one will
be listed above it, as changes since 0.9.216.

Where the two editions cannot behave identically, every case is named in
[`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md) and in the manual section it belongs to.

Downloads: https://www.curseforge.com/minecraft/mc-mods/trmt-reimagined or https://modrinth.com/mod/trmtr
Source: https://github.com/Xeptix/TRMTR

---

### On Minecraft 1.7.10

#### The ground underfoot

- Worn ground is a hollow rather than a picture: it sinks as it wears, down to half a block, with
  real collision behind a mixin, so the dip that is drawn is the dip that is walked in.
  An item dropped into a rut lies in it, rather than being flung back out by the game's own
  escape from a block it was never inside - which is what made drops hop and skitter on a worn road.
- Eighty gradations of wear per square, each with its own picture, so a road darkens and deepens by
  degrees rather than in jumps. A position keeps its
  depth separately from how worn it looks, so sinking never restarts the wear that earned it.
- A long walk arrives evenly rather than nearly all at once at the end of it, and a long run never
  replays the same eight pictures.
- Ground worn the whole way through gives way, and the rut may then go deeper into what was underneath
 - but never where a sapling or a flower is holding the square, and never at a wear ceiling set
  below the whole run, neither of which touches a flat, barely-worn path.
- Explosions scour the ground they do not destroy, scaled to the blast's force, and a hard landing
  marks what it lands on.
- Snow lying on a path takes the traffic first, layer by layer, and only then does the ground beneath it
  begin to mark.
- Worn stone breaks at the speed the server agrees to. A ghost delegated its hardness to the block beneath
  but answered the harvest question for itself, so ground a pack gates behind a better pickaxe broke three
  times too fast on the client and was put straight back by the server.

#### Surfaces, and the ground beneath them

- Grass, dirt and sand were joined by gravel, stone, cobblestone, snow, ice, netherrack and end stone, each
  a family with its own pace.
- Surfaces are detected across a whole pack by block class, material and name rather than listed by hand,
  and what was detected is written into the config so it can be argued with. Blocks that keep
  state of their own are left alone.
- A road can wear through into what is under it rather than into more of itself, so stone shows cobble,
  then gravel, then the earth it was laid on. Grass losing its cover into the earth
  beneath is how it ships and it takes that earth gradually rather than all at the end, with the
  grass side fringe receding with the top; the longer chain for stone, cobble and gravel
  waits on `general.wearThroughToOtherSurfaces`, which ships off, so out of the box a stone road hollows
  out as stone.
- Slabs, stairs and Chisel's layered blocks are ground too, and wear in their own shapes - a slab half as
  deep for exactly as much traffic, a stair blocking light and collision as the stair it stands in for.
- A block's metadata wears what that metadata is made of, rather than what its neighbour is.
- Worn ground keeps what the block had: its glow, its particles, its footing, its transparency, whatever
  showed through its holes, and its shader material until it has worn through.

#### How worn ground looks

- Every wear texture is generated from the block's own pixels, taken out of the running game's atlas, so a
  modded surface wears in its own colours instead of vanilla's. The authored art was
  decomposed into a reusable per-pixel coverage sequence to make that possible.
- Eleven wear looks, each worked entirely from the block's own pixels, for the surfaces nobody drew art
  for - rubbed, cracked, cracked-and-rubbed and the rest. A picker
  draws each of them on the player's own blocks before a choice is made.
- Textures are composed on every core the machine has, and rebuilt when a wear setting moves
  rather than on the next restart.
- The atlas is planned into the room it actually measured, with each sprite priced at the size it is
  stitched at rather than assumed, and every stitch checked against that plan. Each sprite is
  composed once, at the edge its plan priced, with the border anisotropic filtering wraps round a face
  cropped away instead of drawn into the block.
- Wear pictures are filed by the block itself rather than by the id it happened to carry. Forge moves every
  modded block's id when a world loads and when a client joins a server, so on any world that has outlived
  a change to its mod list a modded block goes on wearing its own pictures.
- A moving inner layer - Chisel's lavastone and waterstone - is budgeted for everything it keeps, not for
  its frames alone, and leaves mipmapping on for the rest of the block atlas while it moves.

#### Recovery

- Healing is lazy: it is worked out from the absolute world clock when a chunk loads, so unloaded ground
  recovers too, and it holds still while nobody is connected - an empty server is not a server whose roads
  grow back.
- Every family has its own recovery time outright, from snow at six in-game days to end stone at four
  hundred, rather than having it derived from what the family cost to wear.
- Rain is a discount on recovery for the earthy families, and there is an opt-in gate that makes a surface
  mend only while precipitation is actually falling on it.
- The heal sweep loses no time to rounding, and never refuses a gradation that is owed
  exactly. It also stopped deleting spawn wards, lights and reinforcement it happened to sweep
  past.

#### Tools

- The hand tamper shapes ground deliberately, with its grades read from config rather than hard-coded.
  The chunk tamper works an area, carrying its grade in the stack. The magic tamper
  edits the rules themselves.
- The Wayfarer's Tamper is the end of the line: its own metal, its own silhouette, a reinforce dial, a flat
  one-material cost, a tiered recipe, and a place in world-gen loot a pack can move - so it is
  findable, earnable and craftable even in a pack that cannot make a diamond chunk tamper.
- Mending costs what it should. Each kind of ground inside a patch pays for itself from material that
  square would take, rather than the whole patch being priced by the block that was clicked; the chunk
  tamper charges by the gradation rather than by the square, so a deep mend costs eight times a shallow
  one; and mending that costs nothing earns no experience, which closed an experience farm.
- Four per-material tampers were retired in favour of three items, each carrying its grade in its own data.
- What a reinforcement costs is named per pack rather than built in, and a container named that way
  hands its empty back through its own rule whether a fluid registry knows about it or not - which is
  what GT New Horizons' two concrete buckets need, and they are named in the default list.

#### The three tamper modes

- **Reinforcement** blast-proofs a block and holds it against feet. It did not, in
  fact, withstand a blast until: the rule, the ceilings and the tooltip all existed and nothing
  asked.
- **Spawn ward** bars hostile or passive mobs from a square.
- **Path light** lights it.
- Each stores its state on the same compact per-position record, each explains itself where somebody is
  asking, each has an achievement and a craftable unlock book rather than a gamble, and each switches off cleanly - including its book, its quest and its achievement.

#### The Golem of Ways

- A keeper that holds a stretch of ground at the level it is set to, rebuilt from the iron golem
  so it reads as one, with per-block orders, a screen that tells the truth, and a home of its own.
- Eleven upgrades - nine that change one thing each, among them Far Ways, Deep, Frugal, Stout, Fierce and
  Settled, and two that carry the whole set: the Unstable All Ways, which is every upgrade and holds none
  of them reliably, and the settled All Ways at Once, which does.
- It carries its tamper rather than merely swinging it, works that tamper by the same rules a player does,
  walks to work it cannot reach, keeps a list of jobs nearest home first, and will not work ground it
  cannot show you.
- It eats what roads are made of, one mouthful at a time, handing the empty container straight back, and
  lays reinforcing material where it is wanted rather than spitting it on the floor. It eats with both arms up and its head dipping to meet them. A dead
  golem gives its contents back whatever the loot rules say.
- Automation may feed it the ground it mends with and nothing else, and take nothing out - a hopper
  gives back its tamper and its fitted upgrade. Its price follows the chunk tamper's in the
  same version, and it remembers ground its stores cannot pay for instead of walking every container again
  each stroke.

#### Plants, cover and trampling

- The plants standing in a route go down with the route, stalks and all, when the ground drops a level
 - but a plant's roots hold the square it stands on, and that square is drawn and walked
  at full height on both sides.
- Trampling wears leaves where they are actually walked on, keeps a tally against a plant that fades at
  healing's own rate rather than being wiped by every sweep, and scales its threshold once instead of twice.
  Both trampling switches ship off, because both destroy real blocks.
- A plant or leaf carrying reinforcement or a spawn ward is never trampled, and keeps what was paid for it.

#### Two draughts

- The Potion of Lightness was carried across with its ingredients intact, made at a bench rather than
  brewed, because 1.7.10 has no brewing API. The Draught of the Heavy Foot is its opposite,
  invented on vanilla's own terms in the same version.
- The two cancel rather than one winning outright, and neither is removed when they overlap.
- A disagreement about an effect id is survivable: the client is told, and carries on.

#### Maps and other mods

- JourneyMap is handed a colour that travels toward what the ground is becoming rather than saying it has
  become something else, with an optional desire-path highlight and a depth the player sets.
- Xaero's Minimap shows worn ground, and is told when it changes - it caches every tile it writes and
  nothing here sends a block packet to mark one dirty.
- A path through modded turf stopped reading as a bright green stripe, which it did because that turf's map
  colour is green before the biome's tint is applied to it. Gravel and end stone stopped reporting
  the map colour of the wrong material.
- Waila reads worn ground and unworn ground alike, showing reinforcement and wards on a square that
  has not yet been stepped on.
- Every integration - Waila, JourneyMap, Xaero's, Angelica/Iris, Chisel, GregTech, Amazing Trophies,
  BetterQuesting - sits behind a check that the other mod is present, and none of their code is linked
  against.

#### Multiplayer and dedicated servers

- A player's subscription follows the rules for as long as they stay connected: `/trmt enable`, a
  reload, or a switch to real ruts reaches everybody already online rather than waiting for them to
  reconnect.
- A client uses its server's surface table, rather than each side building its own out of its own config and
  nothing compared the two, so a client could draw ground the server was not wearing and collide with a
  floor the server did not have. The table travels compressed, is used for the visit only, and is handed
  back on disconnect.
- Geometry and pricing travel with the rules - the depth of a gradation, each family's ceiling, the
  successor lists that decide what a road wears through into, and the switches that gate them.
- Updates go to exactly the players the server has sent that chunk to, ground the server writes over is
  repainted rather than left as it was drawn, and a square a plant is holding is flat on both sides.
- A dedicated server with Chisel starts, `/trmt demonstrate` runs on one, and a `/trmt` command arriving
  from a chat bridge, a web panel or RCon is run on the server thread.

#### Books, achievements, quests and finding the tools

- Four guide books with a reading screen of their own, rebuilt to read as books.
- Sixteen achievements, none of them offered where the feature behind it is switched off.
- Quest and trophy definitions written as BetterQuesting's and Amazing Trophies' own data files rather than
  linked against either, landing where the reader reads and leaving no litter in somebody else's tree.
  The quest chapter follows the feature switches.
- The tools and books turn up while exploring, and optional first-join items go to anyone who has never had
  one rather than only to a fresh spawn.

#### Settings, and the screens that edit them

- Config changes apply without a restart, reach ground that already exists, and show each block's icon
  beside its entry.
- A read-only Wear Table became an editable one, in plain language, with editable header figures, a preview
  of the run it is about to publish, and a hold for the block cycle.
- Per-family cost curves let the price of a gradation change along a run - turf tears at a touch, snow
  packs, ice glazes, dressed stone stays flat - normalised so a run's total never moves. The
  editor quotes a price with the curve it was worked out under, solves against it, and draws all five
  against one datum.
- Five preset choosers give ready-made answers that can still be argued with, and a two-slot
  snapshot tool commits and undoes a whole config. A snapshot survives the server stopping: it is a
  whole config file, a few hundred kilobytes of it, so it is stored as bytes rather than as an NBT
  string - which cannot carry more than 65535 of them.
- An operator can push block lists and settings to a server, cap how worn a world is
  allowed to get, and keep the family and mobs editors to operators.
- The magic tamper's "stop this wearing the ground" writes the mob down at nought rather than deleting its
  name, which no wildcard line overrides.

#### Commands and demonstrations

- `/trmt` reloads, reports, enables, disables, purges and pins.
- `golem` says why the golem in front of you is not eating what you have thrown at it, down to what the
  item actually is, what each entry of the material list resolves to, and the line that would join them.
- `showcase` and `demonstrate` build a complete exhibit of every detected surface, look and golem upgrade,
  laid out like a page, clearing their own space and always standing in the same one, within walking
  distance and with the ground loaded first.

#### Old worlds, and removing the mod

- A worn path survives its own rules being changed, and a block broken by accident keeps its
  record for fifteen minutes in case it comes back.
- A save that names the four retired per-material tampers opens again. Forge was being asked to register
  one item under two names and declared the whole world corrupted; the retired names are let go instead,
  and their ids blocked so nothing later can claim them.
- Deleting the jar leaves every block as it always was. The mod registers sixty-six cosmetic ghost blocks,
  so their names are in `level.dat`'s id map and Forge shows its mismatch screen once; nothing was ever
  placed, and continuing past it is safe.

---

### On Minecraft 1.12.2

#### How a worn square is drawn here

- **One ghost block carries every appearance.** On 1.7.10 a block's appearance is a metadata value
  and an icon per side, which takes sixty-six block classes to cover sixty-six shapes; here it is a
  blockstate and a baked model handed the position, so one class covers all of them at eighty
  gradations each - far more than blockstate variants could reasonably enumerate. Sprites are
  generated into the block atlas at stitch time, the model chooses one per position, painting into
  the client's own copy of the world survives threaded chunk rebuilds, and the sunken collision
  needs no mixin at all.
- **Three of the 1.7.10 wear pipeline's mixins were not needed** - the atlas, the collision and the
  grass tint - because Forge 1.12.2 lets a sprite declare dependencies, posts a collision event with
  the boxes about to be returned, and takes an `IBlockColor` per position.

#### The ground underfoot

- Wear kept per chunk, surviving a restart, and the detection that decides which blocks are
  ground at all. Ground that wears as time passes and as it is walked on.
- Wear reaching a client, and then a worn path that can be seen and stood in - the
  point at which the two halves met.
- Worn ground drawn in each block's own pixels, every wear pattern,
  the sides of a rut, and a ghost the shape of whatever it covers.
- **One ghost block, where the other edition has sixty-six**, because appearance is a blockstate and a
  model here rather than a class per shape.
- A worn square repainted the instant the server writes its own block over it, rather than within two
  seconds. This edition's second mixin, and the rescan behind it stays for the case no packet
  announces.

#### Tools and the things they do

- The hand tamper and the cube tool. Reinforcement and the spawn ward, the
  path light, and the Wayfarer's tamper that ends the line.
- What a tamper costs, asked of the ore dictionary rather than of whether GregTech is installed.
  The two draughts, the three enchanted books that unlock the modes.
- The two tools for tuning: a config snapshot and a wear comparison.

#### The Golem of Ways

- The creature as the server knows it and as it is drawn. Its screen. Its
  eleven upgrades have recipes, and the whole-set tier binds in two steps.
- 1.12.2 has no render passes, so what was an extra pass is a `LayerRenderer` handed the posed model.

#### Screens

- The tamper's settings, the in-game config screen, the Wear Table, the wear
  editor, and the guide books with their reading screens.
- A setting that takes effect when you change it, which on 1.12.2 means a resource reload for
  anything that lives in the atlas.

#### Books, advancements, quests and trophies

- **The sixteen achievements became sixteen advancements**, which is the one place this
  edition cannot be 1:1: 1.12 removed the achievement system. Same sixteen, same lang keys, described
  as JSON. A JSON file cannot be registered conditionally, so every switch an achievement answered to
  is read where the advancement is granted instead.
- The quest chapter and the trophy definitions, written as the other mods' own JSON and linking neither.
  Both file shapes are the other edition's and unverified on 1.12.2; both do nothing when
  their mod is absent.
- The chest finds, rebuilt rather than carried, because 1.12.2 replaced Forge's chest categories with
  loot tables. The other edition needs a special entry to hand over a particular enchanted
  book; a loot entry here carries the NBT and needs none.
- The starting items a packmaker can ask for, handed out on any login where that player has never had
  that item in this save.

#### Reading the world

- The inspection, and the last two of the twenty-one packets. The block tooltip, which is
  Hwyla here and Waila there.
- **The two minimap integrations are gone and nothing replaced them**. The other edition
  carries three hundred and fifty-seven lines of reflection against JourneyMap and a hundred and
  nineteen against Xaero's, because there a block is asked its colour with nothing but a metadata.
  1.12.2 hands the position in, so one override answers for every map at once. It found a real gap on
  the way: the ghost had no `getMapColor` at all, so every worn square painted dirt brown whatever it
  was made of.
- A librarian never sells a book for a switched-off unlock. This edition's first mixin.

#### Commands

- The `/trmt` family: status, enable, disable, purge, reload, surfaces, here, golem and mapcolour,
  then showcase and demonstrate with the yard of pens behind them.
- `/trmt mapcolour` answers a different question here, because the grey-map bug it was written to find
  cannot happen: it reports whether the square under your feet draws the same colour as the ground
  beside it.

#### Settings

- **`TrmtConfig` is the other edition's file, not a translation of it**. Three thousand lines
  of settings, carried by a script that makes only the renames that are pure renames between the two
  Forge versions, so a packmaker's edits move between the two editions unchanged.
- Four of its map settings describe a colour 1.12.2 cannot produce, and the mod names them in the log
  as it loads rather than ignoring them quietly.
- **The mod reads and writes its own settings file**, in Forge's format with none of Forge's
  code. `ConfigFile` is a reader and writer of the same `.cfg` the other edition uses - the same
  categories, the same `B:`/`I:`/`D:`/`S:` prefixes, the same nesting, the same CRLF - so one settings
  file is shared by both editions and will be shared by the ones after them. Proved in the running
  game by a verdict that writes a file with this reader and reads it back with Forge's, which is the
  one direction no unit test can cover: Forge's `Configuration` reaches for FML's logging in its
  constructor and throws outside a game. The other direction has fourteen tests against bytes Forge
  actually wrote, two of them sliced from a real 326 KB config.
- **The in-game config screen is still Forge's**, behind an adapter. Opening it builds a throwaway
  Forge config from the real settings; Forge's `GuiConfig` edits that; pressing Done copies the
  differences back. Forge's screen brings the category navigation, an editor per type, the array
  editor with its icons per row, the comment tooltips, the two sliders, per-setting and whole-screen
  reset and the restart warning, and none of that was worth reimplementing on an edition that
  already works. One file, and the one file a later edition deletes rather than ports.

#### Kept honest

- **Twenty-eight classes are shared with the 1.7.10 edition byte for byte**, and a test in each
  repository compares both copies and fails the build on any difference.
- A client-only reference scan, so no client class is ever reached from a server path; a two-process
  probe that joins a real dedicated server and reports a verdict per feature; and 347 unit tests that
  run with no Minecraft on the classpath.
- **A second fence beside that one**, drawn where a port to another loader would cut: fifty-seven
  files across seven packages may name Minecraft and may not name Forge, and the twelve files that
  sit outside it each say what they need Forge for. It fails both ways, so neither the fence nor the
  list of exceptions can rot. What those twelve need reduces to five things, which is the number
  worth knowing: a world by dimension id, chunk persistence, the collision hook and the event bus,
  asking whether a block is a plant, and asking whether another mod is present.

