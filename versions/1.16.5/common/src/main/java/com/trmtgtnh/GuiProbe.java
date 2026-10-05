package com.trmtgtnh;

/** Throwaway. The block stand-ins and the dynamic-texture plumbing the screens need. */
final class GuiProbe {

    static void blocks() {
        net.minecraft.world.level.block.Block[] stand = {
            net.minecraft.world.level.block.Blocks.GRASS_BLOCK, net.minecraft.world.level.block.Blocks.DIRT,
            net.minecraft.world.level.block.Blocks.SAND, net.minecraft.world.level.block.Blocks.GRAVEL,
            net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.COBBLESTONE,
            net.minecraft.world.level.block.Blocks.NETHERRACK, net.minecraft.world.level.block.Blocks.END_STONE,
            net.minecraft.world.level.block.Blocks.SNOW_BLOCK, net.minecraft.world.level.block.Blocks.ICE, };
        net.minecraft.world.item.Item held = stand[0].asItem();
        System.out.println(held);
    }

    static void textures(int[] argb, int edge) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        com.mojang.blaze3d.platform.NativeImage image =
            new com.mojang.blaze3d.platform.NativeImage(edge, edge, false);
        image.setPixelRGBA(0, 0, argb[0]);
        int read = image.getPixelRGBA(0, 0);
        net.minecraft.client.renderer.texture.DynamicTexture texture =
            new net.minecraft.client.renderer.texture.DynamicTexture(image);
        com.mojang.blaze3d.platform.NativeImage held = texture.getPixels();
        texture.upload();
        net.minecraft.resources.ResourceLocation at = client.getTextureManager()
            .register("trmtgtnh_look", texture);
        client.getTextureManager()
            .release(at);
        System.out.println(read + String.valueOf(held) + at);
    }
}
