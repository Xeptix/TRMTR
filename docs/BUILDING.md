# Building

Each edition is its own complete Gradle build. There is no root build and no wrapper that builds
everything; you go into the edition you want and build it.

```bash
cd versions/1.7.10 && ./gradlew build
```

```bash
cd versions/1.12.2 && ./gradlew build
```

The jar you want is `build/libs/trmtr-<mc version>-<version>.jar`. The `-dev` and `-sources` jars
beside it are for development.

Run `./gradlew spotlessApply` before building, or the build fails on formatting.

## What each edition needs

| | 1.7.10 | 1.12.2 |
|---|---|---|
| JDK to run Gradle with | **25** (the GTNH plugin requires it) | 17 or newer |
| Compiles to | Java 8 bytecode, via Jabel | Java 8 bytecode |
| Gradle | wrapper, GTNH conventions over RetroFuturaGradle | wrapper, 9.7.0, RetroFuturaGradle 2.x |
| Forge | 10.13.4.1614 | 14.23.5.2847 |
| Network | `nexus.gtnewhorizons.com` | Cleanroom's repository and CurseMaven |
| Tests | 410 across 43 classes | 347 across 29 classes |

The first build of either downloads and decompiles Minecraft and takes several minutes. After that
it is quick.

## Why there is no root build

Because the two editions cannot share one. The 1.7.10 build is a stack of GT: New Horizons
convention plugins sitting on RetroFuturaGradle, and those plugins are 1.7.10-only; the 1.12.2
build is RetroFuturaGradle 2.x configured by hand, on a newer Gradle. Making them subprojects of a
single build would mean reconciling two Gradle versions and two plugin stacks to gain a convenience
nobody needs, and would put a released, working build at risk to do it.

So each edition stays exactly what it is in development: a self-contained project you can copy out
of this repository and build on its own.

## Why the portable core is duplicated

Twenty-eight classes appear in both editions, byte for byte. They are the ones that name nothing from
Minecraft at all — the storage layer, the wear chain, the texture planners, the surface table codec,
the quest and loot bookkeeping — and they are duplicated rather than shared for the same reason
there is no root build: each edition compiles against a different Minecraft with a different
toolchain, and there is no shared compilation unit for them to live in.

The duplication is deliberate and it is checked. `CoreMatchesOtherEditionTest` in each edition reads
both copies and fails the build on any difference, naming the files. In a development tree that test
needs an untracked `sibling.properties` saying where the other edition is, and skips when it is
absent; **here the editions are siblings, so the file is committed with a relative path and the check
runs for anybody who clones this repository.** Run `./gradlew test` in either edition and it is one
of the tests that runs.

Consequence worth knowing if you are editing: **a change to any of those twenty-eight classes has to
be made in both editions identically**, and neither edition may reformat them.

Real shared compilation — a `common/` module with loader-specific subprojects — becomes possible
from Minecraft 1.16.5, where Architectury Loom can give you Minecraft without a loader on the
classpath. That is where it will happen, because that is where it can.

## Verifying a change

Beyond `./gradlew build`, which runs the unit tests:

- **The portable core must stay portable.** `CoreStaysPortableTest` fails if any of those
  twenty-eight classes starts naming a Minecraft type.
- **The 1.12.2 edition keeps a second fence.** `LoaderNeutralTest` holds fifty-seven files across
  seven packages that may name Minecraft and may not name Forge, and lists the twelve files outside
  it with what each needs Forge for. It fails both ways, so neither the fence nor the list of
  exceptions can rot. It is there for the ports after 1.12.2.
- **Nothing shared may reach a client-only member.** Both editions carry a scan for that, because
  Forge strips `@SideOnly(CLIENT)` members on a dedicated server and the result is a crash that
  never appears in single player. The scan is development tooling and is not published here.

## The released jars carry a little more than this source

The 1.12.2 jars published with a release also contain `com.trmtgtnh.spike`, a verification harness
that drives a client into a real dedicated server and asks it to check the result. It is how that
edition is proved, every part of it sits behind a `-Dtrmt.spike.*` system property, and it does
nothing whatsoever in a normal game. It is not part of the published source because it is of no use
to anybody but the author, so a jar you build from this repository will be slightly smaller than the
one attached to the release. Nothing a player can reach differs.
