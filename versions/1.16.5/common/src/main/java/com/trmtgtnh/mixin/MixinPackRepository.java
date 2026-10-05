package com.trmtgtnh.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

import com.trmtgtnh.client.texture.WearPackSource;

/**
 * Puts this mod's generated wear textures into every pack repository as it is built.
 *
 * <p>
 * <strong>The only place this mod reaches into somebody else's class, and it is here because there
 * is no other door.</strong> A {@code PackRepository} takes its sources as constructor arguments and
 * offers nothing to add one afterwards - no {@code addPackFinder}, no accessible collection, nothing
 * either loader wraps. Forge and Fabric each inject their own mod resources the same way, so this is
 * the normal arrangement at this version rather than a trick.
 *
 * <p>
 * It replaces the set rather than adding to it, which works whether the field was given a mutable set
 * or an immutable one. The repository is built twice in a single-player session and once on a server,
 * so that is three small allocations in a lifetime.
 *
 * <p>
 * <strong>The body does nothing but call out, and that is not stylistic.</strong> Mixin reads the
 * bytecode of everything a mixin class touches, through an ASM that is older than the JDK this
 * workspace runs on - so a {@code new HashSet<>()} in here sent it to read {@code java.util.HashSet}
 * from a Java 21 runtime and it died on the class file version, taking the game's start-up with it.
 * Anything that is not the injection itself belongs in an ordinary class, where the ordinary
 * compiler and the ordinary classloader deal with it.
 *
 * <p>
 * <strong>It applies on both sides on purpose.</strong> {@link WearPackSource} asks whether anything
 * has planned wear sprites at the moment it is read, and on a dedicated server nothing ever has - so
 * this adds a source that contributes nothing there, and no client-only class is named to find that
 * out. Asking which side we are on would mean naming one.
 *
 * <p>
 * What this assumes about {@code PackRepository} - the field's name, its type, and which of the two
 * constructors does the work - is asserted by {@code PackRepositoryStillLooksLikeThisTest}, because
 * {@code @Shadow} names a field with a string and the compiler cannot check it. Without that test
 * the first sign of a rename is the game failing to start.
 */
@Mixin(PackRepository.class)
public abstract class MixinPackRepository {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    /**
     * Every constructor, deliberately, and without naming a descriptor.
     *
     * <p>
     * There are two - one takes sources alone and hands them to the other - so this fires twice when
     * the short one is used. That is harmless and is the cheaper of two evils:
     * {@link WearPackSource} is equal to every other instance of itself, so the set keeps one however
     * many times this runs, and the repository keys its packs by id in a map besides.
     *
     * <p>
     * The evil avoided is naming the constructor in full.
     *
     * <p>
     * <strong>{@code remap = false} is the load-bearing part, and it is honest rather than a
     * workaround.</strong> A constructor is called {@code <init>} in every namespace there is, so
     * there is nothing about this name to map - but left to itself the annotation processor writes a
     * fully-qualified entry into the refmap anyway, and because this mixin lives in the common
     * module that entry comes out in Fabric's intermediary names. Forge then reads it, is told its
     * target class is {@code net/minecraft/class_3283}, and refuses. Saying there is nothing to
     * remap is both true and the fix. The target class itself still remaps, because that comes from
     * {@code @Mixin} above and not from here.
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void trmt$offerWearTextures(CallbackInfo callback) {
        this.sources = WearPackSource.alongside(this.sources);
    }
}
