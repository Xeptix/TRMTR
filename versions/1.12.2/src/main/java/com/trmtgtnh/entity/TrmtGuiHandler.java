package com.trmtgtnh.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import com.trmtgtnh.Trmt;

/**
 * The one screen in this mod that has a container behind it, and how it is opened.
 *
 * <p>
 * Vanilla's gui handler addresses a screen by a block position, and a golem does not have one - so
 * its entity id travels in the x argument, which is what everything else in 1.7.10 does with the
 * same problem. The server looks the entity up itself and answers nothing if that id is not a golem,
 * so a forged id opens no window rather than somebody else's.
 *
 * <p>
 * The screen itself is fetched through the proxy: a class annotated client-only must not be named
 * anywhere a dedicated server will load it.
 */
public class TrmtGuiHandler implements IGuiHandler {

    public static final int GOLEM = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int entityId, int unusedY,
        int unusedZ) {
        EntityGolemOfWays golem = golemAt(world, entityId);
        return golem == null ? null : new ContainerGolem(player.inventory, golem);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int entityId, int unusedY,
        int unusedZ) {
        EntityGolemOfWays golem = golemAt(world, entityId);
        return golem == null ? null : Trmt.proxy.golemScreen(player, golem);
    }

    private static EntityGolemOfWays golemAt(World world, int entityId) {
        if (world == null) return null;
        Entity entity = world.getEntityByID(entityId);
        return entity instanceof EntityGolemOfWays ? (EntityGolemOfWays) entity : null;
    }
}
