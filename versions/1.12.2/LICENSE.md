# Licence

**TRMT Reimagined** is licensed under the **Creative Commons
Attribution-NonCommercial 4.0 International Public License (CC BY-NC 4.0)**.

- Full legal text: <https://creativecommons.org/licenses/by-nc/4.0/legalcode>
- Plain-language summary: <https://creativecommons.org/licenses/by-nc/4.0/>
- A verbatim copy ships in this repository and inside the built jar as `LICENSE_trmt`.

That covers the whole distributed work: the code, the generated wear patterns, the art under
`src/main/resources/assets/trmtgtnh/`, and the jar built from them.

## Why this licence, and not another

TRMT Reimagined is Adapted Material of **The Roads More Travelled** by *milkucha*, which is
itself licensed CC BY-NC 4.0. Section 3(a)(4) of that licence says that if you share Adapted
Material, the licence you apply to it must not prevent recipients from complying with the
original licence. Section 2(a)(5)(b) says the same thing from the other direction: you may not
impose additional or different terms that restrict what a recipient may do with the licensed
material.

That rules out the obvious alternatives. MIT and Apache-2.0 would tell a recipient they may
sell this, which the original forbids. Plain CC BY would drop the NonCommercial term the
original requires be carried through. "All rights reserved" would strip the reuse rights the
original grants. CC BY-NC 4.0 is the licence that matches, so it is the licence this work uses.

This is a plain statement of how the licences fit together, written by the author of this
version. It is not legal advice.

## Attribution

This work is based on:

> **The Roads More Travelled (TRMT)**
> Copyright (c) 2026 **milkucha**
> Source: <https://github.com/milkucha/trmt>
> CurseForge: <https://www.curseforge.com/minecraft/mc-mods/the-roads-more-travelled>
> Licensed under Creative Commons Attribution-NonCommercial 4.0 International
> (CC BY-NC 4.0).

The original is provided as-is and without warranties of any kind. See **Section 5,
Disclaimer of Warranties and Limitation of Liability**, in `LICENSE_trmt`. The same disclaimer
applies to this version.

## This is a modified work

TRMT Reimagined is a **modified version** of The Roads More Travelled. It is not the original,
and it does not behave like the original in several respects that are documented in the README.
In outline, this version:

- was ported from Fabric 1.20.1 to Minecraft 1.7.10 / Forge, targeting GT: New Horizons;
- was re-architected so wear is server-side data painted client-side, rather than blocks
  written into the world;
- generates its wear textures from each block's own pixels, decomposing milkucha's authored art
  into reusable patterns rather than shipping it as finished textures;
- adds surface families, wear gradations, physical sinking, lazy offline healing, automatic
  whole-pack surface detection, and the `/trmt` command set.

The full running record of changes, including everything modified before this rebrand, is in
[`ATTRIBUTION.md`](ATTRIBUTION.md). That record is kept and extended, never replaced, because
Section 3(a)(1)(B) of the licence requires an indication of previous modifications to be
retained.

## NonCommercial

CC BY-NC 4.0 permits use, modification and redistribution **for NonCommercial purposes only**.
The licence defines NonCommercial as "not primarily intended for or directed towards commercial
advantage or monetary compensation".

In practice, for this mod:

- Do not sell it, or sell anything whose value depends on including it.
- Do not put it behind a paywall, a paid download tier, a monetised or ad-gated link
  shortener, or a donation wall that gates access.
- Do not bundle it into a paid modpack, a paid server, or a paid server perk.
- Sharing it freely, forking it, modifying it, and including it in a free modpack are all fine,
  provided the attribution above travels with it and you do not add terms that would stop the
  next person doing the same.

This term is not optional and it is not something this version can waive. It comes from the
original licence and binds everyone downstream.

## No endorsement

This is an unofficial project. It is not produced, endorsed, supported or approved by milkucha.
The CC BY-NC licence grants no trademark or patent rights (Section 2(b)(2)) and nothing in it
may be read as implying a connection with the original author (Section 2(a)(6)). The name
"TRMT Reimagined" reuses the original's initialism as a plain statement of what this work is
derived from, not as a claim of affiliation.

## This version

TRMT Reimagined is by **Xep**. New code and assets written for this version are the author's
own contributions to the Adapted Material, offered under the same CC BY-NC 4.0 terms so the
whole work stays consistent and recipients can comply with the original licence.

If you redistribute this mod, ship `LICENSE.md`, `LICENSE_trmt` and `ATTRIBUTION.md` with it,
and reproduce the attribution above on any download page.
