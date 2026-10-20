package com.trmtgtnh.forge;

import java.util.function.Consumer;

import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

import com.trmtgtnh.block.BlockGhost;

/**
 * The ghost's hitting and breaking dust, held for the square's family (0.9.227; spec GF19), handed to Forge as the
 * ghost's client extensions.
 *
 * <p>
 * Forge moved {@code addHitEffects} and {@code addDestroyEffects} onto a block's client extensions, which the shared
 * module cannot name, so each is passed straight to the ghost's own method here - the answer, and the reason for it,
 * stay the ghost's - as NeoForge's client setup hands them over. Called only from {@code Block.initializeClient},
 * which Forge runs on the client alone; see {@code MixinGhostFriction}.
 *
 * <p>
 * From Forge 66 (26.3) the hitting dust's extension is asked with the face alone, not the hit, so it cannot say which
 * square is being hit (IClientBlockExtensions, read from 26.3-66.0.13). It is left to Forge's default here, and
 * {@code MixinGhostHitDust} hands the ghost the square at the head of {@code ClientLevel.addBreakingBlockEffects},
 * where Forge asks the extension; the breaking dust is still handed over here, with its square.
 */
public final class GhostDustForge {

    private GhostDustForge() {}

    public static void give(Consumer<IClientBlockExtensions> consumer) {
        consumer.accept(new IClientBlockExtensions() {

            @Override
            public boolean addDestroyEffects(net.minecraft.world.level.block.state.BlockState state,
                net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                net.minecraft.client.particle.ParticleEngine manager) {
                return state.getBlock() instanceof BlockGhost
                    && ((BlockGhost) state.getBlock()).addDestroyEffects(state, level, pos, manager);
            }
        });
    }
}
