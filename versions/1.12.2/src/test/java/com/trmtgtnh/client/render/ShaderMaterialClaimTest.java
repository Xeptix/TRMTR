package com.trmtgtnh.client.render;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * What a ghost tells a shader pack it is made of, by the 1.7.10 edition's rule.
 *
 * <p>
 * That edition's {@code ShaderMaterial.claim} gives the covered block while the square still wears as
 * that block's own family, and the block of the family its wear has reached once it does not - so the
 * shine goes as a stone road breaks up, through cobblestone and gravel to earth. Until 0.9.219 this
 * edition gave the covered block until the picture ran out and earth after it, and a stone road worn to
 * its cobble stage went on telling the pack it was stone. Asked of the real blocks, with the game loaded.
 */
class ShaderMaterialClaimTest {

    @BeforeAll
    static void loadTheGame() {
        Bootstrap.register();
    }

    private static IBlockState state(Block block) {
        return block.getDefaultState();
    }

    @Test
    void a_square_wearing_as_its_own_family_claims_the_block_it_covers() {
        IBlockState granite = Blocks.STONE.getStateFromMeta(1);
        assertSame(
            granite,
            ShaderMaterial.claimed(granite, SurfaceFamily.STONE, SurfaceFamily.STONE),
            "a worn granite road is granite as far as the pack is concerned");
        assertSame(
            state(Blocks.GRASS),
            ShaderMaterial.claimed(state(Blocks.GRASS), SurfaceFamily.GRASS, SurfaceFamily.GRASS),
            "a scuffed lawn is still grass");
    }

    @Test
    void a_square_worn_into_another_material_claims_that_material() {
        IBlockState stone = state(Blocks.STONE);
        assertSame(
            state(Blocks.COBBLESTONE),
            ShaderMaterial.claimed(stone, SurfaceFamily.STONE, SurfaceFamily.COBBLE),
            "a stone road worn to its cobble stage still claimed stone - the clause this port had left out");
        assertSame(
            state(Blocks.GRAVEL),
            ShaderMaterial.claimed(stone, SurfaceFamily.STONE, SurfaceFamily.GRAVEL),
            "worn to grit, it is gravel");
        assertSame(
            state(Blocks.DIRT),
            ShaderMaterial.claimed(state(Blocks.GRASS), SurfaceFamily.GRASS, SurfaceFamily.DIRT),
            "a lawn worn bare is earth, not grass");
    }

    @Test
    void with_nothing_recorded_it_claims_what_the_wear_has_reached() {
        assertSame(state(Blocks.SAND), ShaderMaterial.claimed(null, null, SurfaceFamily.SAND));
        assertSame(state(Blocks.NETHERRACK), ShaderMaterial.claimed(null, null, SurfaceFamily.NETHER));
        assertSame(state(Blocks.END_STONE), ShaderMaterial.claimed(null, null, SurfaceFamily.END));
        assertSame(state(Blocks.SNOW), ShaderMaterial.claimed(null, null, SurfaceFamily.SNOW));
        assertSame(state(Blocks.ICE), ShaderMaterial.claimed(null, null, SurfaceFamily.ICE));
        assertSame(state(Blocks.DIRT), ShaderMaterial.claimed(null, null, SurfaceFamily.DIRT));
    }

    @Test
    void the_bare_earth_is_earth() {
        assertSame(
            state(Blocks.DIRT),
            ShaderMaterial.claimed(state(Blocks.STONE), SurfaceFamily.STONE, null),
            "where the model draws the bare earth, the claim is earth whatever was underneath");
    }
}
