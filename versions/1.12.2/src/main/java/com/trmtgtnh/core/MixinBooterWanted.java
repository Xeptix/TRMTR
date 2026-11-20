package com.trmtgtnh.core;

import java.util.Collections;
import java.util.Set;

import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.VersionParser;

import com.google.common.eventbus.EventBus;

/**
 * The requirement on MixinBooter, made only in a game that has no Mixin at all (G17 R5, 2026-11-11).
 *
 * <p>
 * Until then {@code Trmt} required MixinBooter outright, and Forge refused any game without it - including RLCraft,
 * where MixinBooter 11.13 cannot start because the pack boots an older Mixin first. So the annotation now only orders
 * this mod after MixinBooter, and where {@link TrmtLoadingPlugin} finds no Mixin at all it hands FML this container,
 * whose one requirement is MixinBooter. Forge's own check reads it, as it read the annotation, and the player sees the
 * same missing-mod screen naming MixinBooter under this mod's name. Nothing in it is ever constructed: the game stops
 * on that screen first.
 */
public final class MixinBooterWanted extends DummyModContainer {

    /** Its own id, never this mod's: a second container under the mod's id is a duplicate Forge refuses. */
    public static final String ID = "trmtgtnh_mixins";

    /** What it requires, in Forge's grammar: MixinBooter, any version, as the annotation said. */
    public static final String REQUIRES = "mixinbooter";

    private final Set<ArtifactVersion> requirements;

    public MixinBooterWanted() {
        super(metadata());
        this.requirements = Collections.singleton(VersionParser.parseVersionReference(REQUIRES));
    }

    private static ModMetadata metadata() {
        ModMetadata md = new ModMetadata();
        md.modId = ID;
        md.name = "TRMT: Reimagined";
        md.version = com.trmtgtnh.Tags.VERSION;
        md.description = "Asks for MixinBooter, which this mod's mixins need where no other mod brings Mixin.";
        return md;
    }

    @Override
    public Set<ArtifactVersion> getRequirements() {
        return requirements;
    }

    /** Active, because Forge checks the requirements of active containers only. */
    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }

    /** Nothing to construct, so Forge never asks for a mod object it would not find. */
    @Override
    public boolean isImmutable() {
        return true;
    }
}
