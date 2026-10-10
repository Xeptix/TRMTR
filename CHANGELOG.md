# TRMT: Reimagined - changelog

One version number across every edition. 0.9.222 is the same release on Minecraft 1.7.10, 1.12.2
and 1.16.5, and later releases will carry whatever editions exist then. A change in one edition
moves the number for all of them, so an edition's entry saying nothing changed is saying something true.

## 0.9.222 - 2026-10-10

**The 1.12.2 golem no longer crashes the server when it has no tool.** An unarmed golem - one just built, or set down
from an egg, or whose last tamper broke - handed the game an empty hand the wrong way every tick, and the server threw.
Shift-clicking a golem's storage could throw on both later editions too. A golem keeps its free slots and hands the
game what it expects.

*Changes: 1.12.2 and 1.16.5.*

**Wear survives saving on 1.16.5 Fabric.** The mod let go of the world's wear as the server began to stop, before its
last save, so every chunk that final save wrote went out without its wear. It now lets go once the world is saved,
as Forge and the older editions always did.

*Changes: 1.16.5 Fabric.*

**A golem can be built on 1.16.5.** Its body is named in the settings the way 1.7.10 names it, and 1.16.5 had renamed
stone bricks, so no golem could be built without a mod that supplied the old name. A 1.7.10 block name in the
settings now reads as the block it became. A head hung on a wall builds one too, as it does on 1.7.10, and the golem
defends a villager from a monster round a corner rather than only one in sight.

*Changes: 1.16.5.*

**Villagers wear paths on 1.16.5 out of the box.** The mob list ships as `Villager` in every edition, and 1.16.5 asked
for mobs by their registry name, so nothing in the list ever matched. A mob written the 1.7.10 way is now read as the
mob it became - every kind of it where later versions split one, so `Skeleton` counts wither skeletons and
`EntityHorse` every horse - on 1.12.2 as well.

*Changes: 1.12.2 and 1.16.5.*

**Bone meal mends worn ground on 1.12.2 and 1.16.5**, as it always has on 1.7.10: a scattered patch, a block of the
ground a handful while the cost is on, spent only when something was mended.

*Changes: 1.12.2 and 1.16.5.*

**Ground under a carpet wears, snowed lawns look right, and worn sides draw their own picture** on the later editions:
a carpet stands a sixteenth high from 1.8 on, so steps on it were never counted; a worn lawn's underside under snow,
grass that has sunk, and a modded turf with its own snowy side now draw as on 1.7.10, and a side slides only where it
is the block's own side picture.

*Changes: 1.12.2 and 1.16.5.*

**A chunk's wear is forgotten when it leaves.** The client kept a chunk's worn squares and lights after the chunk
unloaded, and kept every dimension's after a change of dimension, so a road healed while you were away came back
painted, and one dimension's roads appeared on another's ground at the same coordinates.

*Changes: 1.12.2 and 1.16.5.*

**1.16.5 catches a chunk up when it loads**, healing what it was owed while it was away, as the older editions do;
until now it healed only when the sweep reached it, and a step on it first threw that healing away. A machine block
from a Forge mod is never treated as ground, and vanilla's plants are vegetation again.

*Changes: 1.16.5.*

**Tampers on a dedicated server's clients show their real grade on 1.16.5**, rather than all reading as iron; on Fabric
the hand tamper lasts as its grade says; an enchanting table offers the three unlocks on a chunk tamper, and on Fabric
no longer on a pickaxe.

*Changes: 1.16.5.*

**Blasts, landings and the golem wear nothing in a dimension the list rules out**, as walking and the tamper never
did. With the mod switched off a chunk that loads is no longer healed. A placement is heard after every other mod, so
a protection mod that refuses it leaves a worn square's record - and a golem's blocks - where they were. The chunk
tamper's pin answers to `tamperCanPin`, its steps to `chunkTamperMaxSteps`, and one broken mid-use leaves the hand.
The magic tamper's "wear as" now holds when it moves a block to an earlier family. `/trmtnotice` typed alone answers
instead of failing.

*Changes: every edition.*

**Settings read as Forge reads them.** On the later editions a number typed outside its range is held to the range,
as 1.7.10's Forge holds it; the file keeps what you typed.

*Changes: 1.12.2 and 1.16.5.*

**Finds and costs.** On 1.12.2 all three unlock books turn up in chests again, not only the reinforcing one, and
vanilla's dungeon, mineshaft, desert and mansion chests no longer hand them out. A loot category written twice doubles
the Wayfarer's odds there, as the setting says. A worn block with no item form is never priced as Air. On 1.16.5
vanilla's chests no longer hand out an unlock book either, trodden turf sheds a seed as often as it should, and a
machine's tamper earns no experience to mend itself with; on Fabric a ward keeps every mob off, the ocelot included.

*Changes: 1.12.2 and 1.16.5.*

**`/trmt` answers only you on 1.16.5**, rather than copying every reply to every operator online, and it answers you
whatever the command-feedback rule says. The demonstrate pens hand out turf rather than tufts of grass, their signs
face the row, their fences join and their chests are one double chest.

*Changes: 1.16.5.*

**A placed block takes its record back only as the same block on 1.16.5**: a top slab where a worn bottom slab stood,
or a log laid on its side, no longer takes the old wear.

*Changes: 1.16.5.*

**An advancement comes with what it hangs from**, as 1.7.10's achievements do - crafting a Reinforcing book also
gives the three before it - and none is awarded with `general.achievements` off.

*Changes: 1.12.2 and 1.16.5.*

**The mod list says whose work this is built on**, with the licence and the notice, on 1.12.2 and both 1.16.5 loaders
as on 1.7.10; and the mod logs under its name on every edition.

*Changes: 1.12.2 and 1.16.5.*

**Words.** Two setting descriptions said what the mod does not do: path light keeps no mob from spawning, and a wear
drop is rolled once per wearing. Every long dash in the source is a plain hyphen.

*Changes: every edition.*

**A worn square sounds, slides and breaks like its ground** on 1.12.2 and 1.16.5, as on 1.7.10: worn sand crunches
and worn ice still slides; it breaks as fast as the block it covers, with that block's hardness; a sapling, cactus or
reed stays planted on worn ground; and its dust is the ground's color, not a lawn's.

*Changes: 1.12.2 and 1.16.5.*

**Worn ground keeps up with what the server sends.** A chunk arriving is painted as soon as its blocks are in, and the
block you dig or build against keeps its wear rather than flashing back to plain ground until the next look; a square
the server clears tells a minimap too. A square with no wear picture of its own draws its own ground, not dirt. On
1.16.5 a worn window's top hides under a window above it, worn lava keeps its pace, and the stairs, fences, walls and
panes beside worn ground keep their shape. A chunk tamper held in the off hand opens its settings without also mending
or pinning.

*Changes: 1.12.2 and 1.16.5.*

**Worn stairs and worn ice look as they do on 1.7.10.** On 1.12.2 and 1.16.5 a worn stair keeps the faces a stair
keeps, its sides no longer show earth or a lawn's fringe, it takes its block's own color, and it is lit like 1.7.10's,
from the brightest light around it; on 1.12.2 the water under worn clear ice is no brighter than under the ice beside
it.

*Changes: 1.12.2 and 1.16.5.*

**Maps show what worn ground has become.** On 1.12.2 and 1.16.5 a minimap or a map item now draws a worn square as the
material it has worn into - a lawn worn through reads as earth - still darkened so a road shows. JourneyMap draws a
square it cannot color from the block beneath it rather than plain grey, fades worn ground toward the ground it is
becoming, and says in the log whether the desire-path highlight is working. On 1.7.10 that last line now prints, and
the warning it answers no longer fires while JourneyMap is asking.

*Changes: every edition.*

**Worn ground survives a change of dimension, and bone meal stays in one hand.** On 1.12.2 and 1.16.5 the paths around
where you arrive in another dimension no longer vanish now and then until you walk away and back, and no old
dimension's square turns up in the new one. Bone meal on a worn path no longer also uses whatever is in your off hand -
a torch or a block was placed against the path on every click. Raising the Wayfarer's loot weight from nought takes
effect without a restart, and a golem's storage slot holding an item from a removed mod is free again.

*Changes: 1.12.2 and 1.16.5.*

**A path light goes out on every screen when its ground goes.** A light lost with its worn square - healed over by the
sweep, broken, or trampled away - stayed glowing on every client that had seen it until the chunk unloaded; now it goes
out at once, and comes back with the block if the same block is put back in time.

*Changes: every edition.*

**Settings and tools say what they did.** The snapshot tool no longer announces a side as loaded when putting it back
failed. `/trmt enable` and `/trmt disable` keep the master switch's help text in the file. A config pushed by an
operator into a LAN game leaves the host's own wear looks and its hiding of wear under blocks alone, as it already left
the client settings. The Wayfarer's Tamper says which mode it is set to, as the chunk tamper does. The Commands guide's
pages say what the commands do. On 1.16.5 `/trmtnotice` answers any words with its usage line rather than the game's
error, and offers no words as you type, as on the other editions.

*Changes: every edition.*

**Development.** The spec checklist - every rule 1.7.10 keeps, cited to its line and marked for each edition - is
written, twenty files of it, and is how the next ports will be held to this one.

*Changes: no edition - the development tree.*

## 0.9.221 - 2026-10-09

**The update notice speaks only of releases that change your own jar.** Every edition takes the new number with
every release, so until now a 1.7.10 player was told to update for a release that changed only 1.16.5. From this
release the version file says, for each jar, the newest release that changed it, and you are told only when one of
those is newer than yours - pointed at the newest release, which carries it. A release that changes nothing in your
jar is one line in the server's log; `general.updateNoticeReleases` set to `all` tells you of every release instead.
A release can mark itself critical for a jar, with a reason - a crash, world data, a broken feature - and anyone
skipping past it is told so ("It includes a critical fix (a crash) from 0.9.218"). A release can also give the chat
one short sentence of what it does. Every word shown is this mod's own: the version file supplies version numbers,
one of three reasons, and a sentence held to fifty characters of plain text with no address in it. Jars from before
this release read the file as they always did, and hear of every release.

*Changes: every edition.*

**The update notice reads easily, and can be quieted.** A headline with the new version in gold and yours in gray,
and under it one thing to a line - the critical fix, what is new, where to get it, the Discord with its invite on a
line of its own, and last, links to silence that one update or turn update notices off. Each asks you to confirm in
the chat, acts for you alone, and is remembered by the server; `/trmtnotice on` turns them back on. A server that
stays up tells everyone who can update again every eight hours, reading the version file afresh each time. Every
line fits the chat's width, so none breaks in the middle of a link.

*Changes: every edition.*

**A required mod older than this one wants is warned about.** UniMixins on 1.7.10, MixinBooter on 1.12.2, Cloth
Config and Fabric API on 1.16.5: the version file gives each a minimum and a recommended version, and whoever can
update the game is told when theirs is older - which version to get, and where to ask for help. The minimums are the
oldest that were run in game with this release, up to five years back: UniMixins 0.1.11, MixinBooter 8.0, Cloth
Config 4.11.26 on Forge and 4.14.54 on Fabric, and Fabric API 0.29.4. MixinBooter 7.0 and older carry a Mixin too old
for any of this mod's mixins to apply. What the mod is built against is what it recommends. This is the softer line
above the loaders' own floors, and it can move without a new release of this mod.

*Changes: every edition.*

**Older Cloth Config is enough on 1.16.5.** The Fabric jar asked for 4.17.132, which Modrinth does not carry - its
newest for 1.16.5 is 4.17.101 - so a player who took Cloth Config from there could not start the game, and the Forge
jar asked for 4.17. They now take 4.14.54 on Fabric, the oldest for 1.16.5 there, and 4.11.26 on Forge, the most
downloaded. Every class and method this mod uses from Cloth Config is in both, checked one by one, and the whole
in-game test ran on each.

*Changes: 1.16.5.*

**Crash reports and errors say where to take them.** Every crash report carries a line under this mod's name: if
the crash names TRMT or com.trmtgtnh, report it at the GitHub issues page, in the Discord's #mc-bug-reports or
#mc-help, or as a reply on the mod's release post. The first error this mod logs in a session is followed by the
same. Read in a crash made on purpose in every edition.

*Changes: every edition.*

**Worn Chisel liquid moves wherever you can see it.** A worn lavastone or waterstone picture carries its own copy of
the lava or water, so every one that moves is an upload of its own, and until now the atlas asked all of them every
tick and let the same 256 move - so worn liquid in view could stand still. A worn picture now moves while it is on
the ground near you, every one, in step with the unworn block beside it, and nothing else on the atlas is redrawn:
on GT New Horizons with Chisel, 212 pictures in view moved ten frames a second each for about six milliseconds a
second, where 5,440 had been asking.

*Changes: 1.7.10 and 1.12.2.*

**Under OptiFine on 1.16.5, a snow layer's side at a rut's step is drawn.** OptiFine decides which faces to draw
from what the two blocks are, not where they stand, so the step between two snow layers at different heights was
left open and a line of the ground showed. Its face test now keeps that step, on the OptiFine it was written for;
another OptiFine draws as OptiFine does and the log says so.

*Changes: 1.16.5.*

**The name is TRMT: Reimagined**, with its colon, in the mod list, the achievement page, the settings and every
document.

*Changes: every edition.*

**Test rig.** The harness can crash a game on purpose to read its crash report, the update notice is photographed
in five more variants, and each second of a run says what the moving layers cost.

*Changes: no edition - the test rig.*

## 0.9.220 - 2026-10-08

**Chisel on 1.12.2 wears as it does on 1.7.10.**

- **Worn lavastone and waterstone show their lava and water**, with the water drawn through the carving's
  open gaps: each gap is left genuinely open, the carved stone stays solid, and the faces behind a worn
  square are drawn so there is something to see through it.
- **Every Chisel carving wears its own pixels**, and so do most 1.12.2 mods' blocks: the mod now reads a
  block's texture from the model Forge built for it, where it had fallen back to the family's stock
  texture, and the liquid is only ever painted into a block's own pixels. Chisel's waterstone and ice wear
  as ground at all, where they had been passed over.
- **All six of Chisel's lava and water stones** are named in `client.innerLayerTextures` by default; a list
  an earlier release wrote, holding the two names it shipped, is given the other four once.
- **All of their lava and water can move.** `client.innerLayerAnimationBudgetMb` is 128 megabytes by
  default on 1.12.2: Chisel's sixty-four lava and water faces hold about 79 at the shipped settings and 101
  with the larger atlas below, and 64, the default everywhere else, kept eight of them still. A config from
  an earlier release that still holds 64 is raised once; a figure you chose is kept.
- **No shell lift on 1.12.2.** On 1.7.10 the carved stone is lifted a hair clear of the liquid, because a
  renderer there makes the two flicker against each other. Photographed close up, worn and unworn, 1.12.2
  shows no flicker - Chisel draws the two as one model - so its two lift settings say they do nothing on
  that edition, and on 1.16.5, whose Chisel has no lava or water stones.

**The block atlas stitches in under a second where it took minutes.** The game's own stitcher places each
texture by searching every filled part of the atlas from the top, and never remembers which parts are
full, so it slows with the square of the number of textures - unnoticeable while they are all one size,
not once they are not. Wearing Chisel's carvings at their own size put four sizes of texture into the
1.12.2 atlas, and the stitch took nearly three minutes. The stitcher now skips any part of the atlas that
has already turned away a texture at least as large, in every edition and on both 1.16.5 loaders; that
pack loads its textures in about eleven seconds, and GT: New Horizons with Chisel on 1.7.10 stitches
111,486 textures in under two. Every texture lands where the game would have put it, short of one the game
would have laid over others, which goes somewhere free instead. The end of every stitch says in the log
what it took.

**Worn ground under OptiFabric keeps the shader material of the block it covers**, on 1.16.5 Fabric, as it
already did under OptiFine, Oculus, Iris and Canvas. 0.9.219 said this was out of reach; it was not.

**1.16.5:**

- **Snow lies on worn ground, and ground wears under snow, carpets and slabs.** Every snow layer, carpet,
  slab and fence counted as a solid cube, so the ground under them was taken as covered and never wore,
  and snow could not stay on a worn square: it found nothing there to rest on. Under Canvas, snow and
  carpet on a rut now come down with it as under every other renderer: Canvas asks whether a block has an
  offset before it asks where to draw it, and snow and carpet said they had none, so they had stood at the
  road's height since 0.9.219 brought them down everywhere else.
- **Worn ground with something see-through behind it is drawn through**, as on 1.7.10: a block given a
  see-through layer in `client.innerLayerTextures` shows it through its gaps once worn, lit from the
  brightest light around it. On Forge a modded block's worn ground is drawn in the block's own render
  pass, where it was always drawn as solid.
- **The wear textures load faster.** The game reads its textures on six threads, and every one of them ran
  the mod's texture pass at once, building most textures twice; it runs once now, on up to eight threads
  as on 1.7.10, and writes each picture in memory rather than through a temporary file - 22,407 pictures
  in about five seconds instead of nineteen. Under OptiFabric the mod loads in half the time.
- **The atlas is measured** before the wear is planned into it, as on 1.7.10, rather than assumed half
  full, and the end of every stitch says what went into it.

**1.12.2:** the end of every stitch says what went to the stitcher against what was planned, as on
1.7.10 - it had been carried over and never called.

**Worn shapes on 1.12.2 and 1.16.5 follow 1.7.10 exactly:** how deep a rut is drawn, the outline you see
when you look at worn ground, which blocks wear as slabs, and how snow rests on a worn block.

**Every edition:**

- **An early build is told when its release is out.** A build handed out before its release - named like
  `0.9.220-snapshot.4`, or `-early.2` - is told, like a release, when the release it was made for is out,
  and never about an older one. Builds held at a stage - an alpha, a beta, a nightly - are read the same
  way. A build marked bad tells its players so, and names the build to use instead.
- **A larger block atlas, if you ask for it.** `client.largerAtlas`, off by default, lets the wear use an
  atlas up to 16384 pixels square where your graphics card says it can address one, for a pack whose
  faces will not all fit an 8192 square at full detail - Chisel on 1.12.2 drew every ramp at 62 of its 80
  gradations without it. A 16384 atlas takes four times the video memory, and the game builds it without
  checking the card accepted it, so a card that cannot hold one draws every block black; the setting's
  description says so.
- **Worn ground keyed on what is behind it.** Two blocks with the same face and different layers behind
  them had shared one set of worn pictures, so one wore the other's liquid. Found by Chisel on 1.12.2, and
  fixed in every edition.
- **"Color", never "colour".** `/trmt mapcolour` is now `/trmt mapcolor`, and the desire-path color you set
  is carried over to the setting's new name, `client.desirePathColor`.

## 0.9.219 - 2026-10-07

**You are told when a newer release is out.** Whoever can update a copy of the mod - the owner of a
single-player world, the host of a LAN game, a server's operators - is told as they join, in one chat
line with links to the CurseForge and Modrinth pages. It reads one small file from this repository once
per launch, sends nothing but the request itself, and is never shown to anybody who could not act on it.
`general.updateNotice` is `operators` by default; `everyone` tells every player, privately, and `off`
tells nobody and never asks. A server that does not ask says why, in one line of its log. Every edition,
both 1.16.5 loaders.

**1.12.2 and 1.16.5 were checked under OptiFine, Oculus, Iris, Canvas and OptiFabric, and fixed where
they fell short.** Each pack loader was photographed under Complementary Reimagined and Sildurs Vibrant,
beside the same renderer without a pack, and again after the pack was turned off mid-session - which
is where 1.12.2 under OptiFine once crashed. Canvas draws through its own pipeline and was photographed
under that.

- **Worn ground keeps its shader material under OptiFine** on 1.12.2 and on 1.16.5 Forge, as it already
  did under Oculus and Iris, and under **Canvas** on 1.16.5 Fabric as the covered block's FREX material.
  A worn path keeps the shine, sway or reflection the pack gives the block it covers while it still shows
  that block, and takes the earth's once it has worn through. Under **OptiFabric** that one thing is out
  of reach: OptiFabric defines OptiFine's classes after every mod's hooks have been prepared, so none can
  bind to them, and a worn square draws with the pack's default material rather than its block's.
  Nothing else about it differs.
- **Embeddium** with Oculus on 1.16.5 Forge, Canvas, and OptiFabric on 1.16.5 Fabric are now all
  photographed with every release. None of them, and no shader loader at all, is ever required.
- **Turning the pack off mid-session under OptiFabric could crash the game**, in Fabric's own renderer,
  which copied each worn square at a size OptiFine had changed while the pack was on. Under OptiFabric
  each corner is handed over on its own now, read at whatever size OptiFine gives it.

**A 1.16.5 Fabric dedicated server with the mod crashed on its first tick**, from 0.9.217 on. Building
its recipes called a method Fabric keeps on the client only - a dedicated server's copy of the game does
not have it - and single player and every client have it, so nothing but a real Fabric server could
show it. It runs now, and every call the mod makes is checked against the game's own client-only marks:
two more were found and moved, one of them in how config snapshots are saved on a Fabric server.

**1.16.5 lit worn ground wrongly in three ways.**

- **Under Rubidium, Embeddium and Canvas the second demonstration yard drew darker the deeper it was
  worn.** The server sends light in packets of its own, and each one replaced the client's light for its
  sections with the server's - which lights the real block standing there, not the hollow the client
  draws. Sunk squares are now lit again when the server's light lands, and when a column is painted
  ahead of its light.
- **A worn stair drew its riser black on Forge**, and **worn ice stopped all light**, so a path worn
  across a frozen lake darkened the water under it. A worn square that has not sunk now stops light
  exactly as the block it covers does, not as a whole block of earth.

**Worn ground that has not sunk shades the corners beside it, on both ports, as the block it covers
does** - so the edge of a rut is shaded toward the step, as on 1.7.10. Both had answered that worn ground
shades nothing at all, and sunk ground beside an unsunken square drew evenly lit. Vanilla's renderer and
OptiFine draw that shade as 1.7.10 does. Forge's own light pipeline on 1.12.2, and Rubidium, Embeddium,
Sodium and Fabric's Indigo on 1.16.5, blend a shallow face's shade by its depth, so there the edge is
softer - as it is for a vanilla path beside a full block.

**With JourneyMap 6 on 1.12.2, none of the mod's mixins loaded.** JourneyMap 6 brings a Mixin of its own,
which starts before MixinBooter, and MixinBooter then reads no other mod's list of mixins. Snow and carpet
stayed up off worn ground, a chunk the server rewrote was not repainted at once, librarians sold no wear
books, OptiFine's shader material went unclaimed - and nothing said so. A small loading plugin now
registers them itself when that happens, and the log says if they still did not apply.

**1.12.2's worn ice hid what was behind it.** A worn ice square let the real ice under it and the worn
squares beside it leave off every face they shared with it, so ice read as one thin pane with whatever
stood beyond showing through. It is drawn through as ice is now, and stops only the light ice stops.

**The two ports caught up with 1.7.10 in the places nobody had looked.**

- **Golems of Ways are built from their blocks and a head again** on both: the builder had been ported
  and was never called.
- **Leaving a server hands everything back.** A server's own rules - its decay mode, switches and family
  numbers - stayed in force into the next world you opened on 1.16.5, because leaving never read your own
  settings back. Joining now carries a server's rules out in full, and leaving undoes them, on both.
- **Snow and carpet on a rut are drawn down with it**, where only the footing had come down.
- **A client with the mod can join a server without it**, on both, as on 1.7.10.
- **Frosted ice is not ground.** Frost Walker lays it and the game melts it; counted as ice, it laid a
  demonstration platform that melted and poured off the yard.
- **Messages the mod speaks from the client reach you on 1.16.5** - every one was being dropped - and the
  in-game guide no longer tells a Fabric player the mod runs on Forge alone.
- **On 1.16.5 the demonstration's roads, its golem pens and JourneyMap's color for worn grass used the
  grass plant and the snow layer** where they meant the grass block and the snow block.
- **Moved block ids rebuild the surface table** on both, and the moving layers behind worn Chisel stone
  move again on 1.12.2.
- **The demonstration lays its stairs rising east**, as 1.7.10's does, on both.

**On 1.7.10 nothing else changed.** The update notice is this edition's whole difference from 0.9.218.

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
a block's map color, out of sixty-four fixed palette entries, so there is no fraction to darken by -
and both had therefore given the darkening up, which left a path showing on a minimap only once it
had worn through into another material. A palette entry can be picked for being darker even though a
color cannot be dimmed, so a worn square now reports the nearest entry to its own color darkened by
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
  modded surface wears in its own colors instead of vanilla's. The authored art was
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

- JourneyMap is handed a color that travels toward what the ground is becoming rather than saying it has
  become something else, with an optional desire-path highlight and a depth the player sets.
- Xaero's Minimap shows worn ground, and is told when it changes - it caches every tile it writes and
  nothing here sends a block packet to mark one dirty.
- A path through modded turf stopped reading as a bright green stripe, which it did because that turf's map
  color is green before the biome's tint is applied to it. Gravel and end stone stopped reporting
  the map color of the wrong material.
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
  nineteen against Xaero's, because there a block is asked its color with nothing but a metadata.
  1.12.2 hands the position in, so one override answers for every map at once. It found a real gap on
  the way: the ghost had no `getMapColor` at all, so every worn square painted dirt brown whatever it
  was made of.
- A librarian never sells a book for a switched-off unlock. This edition's first mixin.

#### Commands

- The `/trmt` family: status, enable, disable, purge, reload, surfaces, here, golem and mapcolor,
  then showcase and demonstrate with the yard of pens behind them.
- `/trmt mapcolor` answers a different question here, because the grey-map bug it was written to find
  cannot happen: it reports whether the square under your feet draws the same color as the ground
  beside it.

#### Settings

- **`TrmtConfig` is the other edition's file, not a translation of it**. Three thousand lines
  of settings, carried by a script that makes only the renames that are pure renames between the two
  Forge versions, so a packmaker's edits move between the two editions unchanged.
- Four of its map settings describe a color 1.12.2 cannot produce, and the mod names them in the log
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

