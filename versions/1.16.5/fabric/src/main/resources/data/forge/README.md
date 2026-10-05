# Why this mod ships tags in somebody else's namespace

Every material list in this mod is written in ore-dictionary names - `ingotIron`, `gemDiamond`,
`blockIron` - because that is what the 1.7.10 edition's settings say and a settings file is meant to
carry between editions. The ore dictionary does not exist at this version, so `OreNames` translates
each name to the tag it means: `ingotIron` becomes `forge:ingots/iron`.

**On Forge that tag exists, because Forge ships it. On Fabric nothing does.** So on the Fabric build
every material lookup answered "nothing in this pack has that", and the mod took its own advice: no
tamper recipe, no chunk tamper recipe, no repairing a tamper with its own metal. Nothing failed and
nothing was logged - the mod's own line for it reads "nought added where a pack supplies no metal
this mod can make a tamper from, which is a quiet answer rather than a fault". It was a fault.

These files make the tag exist, for the vanilla materials the shipped settings name. A modded metal
still comes from whatever the pack puts under its tag, exactly as on Forge, and a pack that would
rather name a tag outright still may - anything with a colon or a slash in it is taken as written.

Only vanilla's own items are listed, and `"replace": false` throughout, so a pack or another mod
adding to any of these tags adds rather than argues. Nothing here is loaded on Forge: this is the
Fabric module's resources, and the Forge jar carries none of it.
