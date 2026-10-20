package com.trmtgtnh.forge.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import com.trmtgtnh.block.BlockGhost;

/**
 * How slippery a worn square is, and its hitting and breaking dust, asked by the names Forge gives those questions
 * (0.9.227; spec GF17 and GF19).
 *
 * <p>
 * <strong>Found carrying the mod to 26.1.2</strong>: the shared ghost answers the three questions under the names
 * Forge used up to 1.16.5 - {@code getSlipperiness}, {@code addHitEffects} and {@code addDestroyEffects} - and Forge no
 * longer asks them by those names. Read from Forge 47.4.27's and 52.1.19's own jars: {@code IForgeBlock} names the
 * friction question {@code getFriction}, with the same four arguments, and the two dust questions moved onto a block's
 * client extensions, {@code IClientBlockExtensions}, handed over through {@code Block.initializeClient}. So on Forge
 * every worn square slid as the ghost's built-in 0.6 - worn ice was no longer ice - and its dust was the game's
 * placeholder rather than the square's family's. NeoForge has had the same bridge since 1.21.1
 * ({@code MixinGhostFriction} and its client extensions there); Forge had none.
 *
 * <p>
 * This adds both Forge-named methods to the ghost, each handing the question straight to the ghost's own answer, so
 * the answer and the reason for it stay {@link BlockGhost}'s. The worn stair's ghost is a ghost too, and inherits them.
 * The client extensions are made by {@link com.trmtgtnh.forge.GhostDustForge}, outside this class, so a dedicated
 * server - where Forge never calls {@code initializeClient} - never loads a client type.
 */
@Mixin(BlockGhost.class)
public abstract class MixinGhostFriction {

    // Public and with Forge's name and descriptor, so it overrides IForgeBlock.getFriction on the ghost.
    public float getFriction(BlockState state, LevelReader world, BlockPos pos, Entity entity) {
        return ((BlockGhost) (Object) this).getSlipperiness(state, world, pos, entity);
    }

    // And Block.initializeClient, which Forge's patched Block calls from its constructor, on the client alone.
    public void initializeClient(Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        com.trmtgtnh.forge.GhostDustForge.give(consumer);
    }
}
