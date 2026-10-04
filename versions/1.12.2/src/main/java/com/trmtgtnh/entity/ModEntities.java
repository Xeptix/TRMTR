package com.trmtgtnh.entity;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import com.trmtgtnh.Tags;
import com.trmtgtnh.Trmt;

/**
 * The mod's entities, registered on both sides.
 *
 * <p>
 * Mod-scoped rather than global. A global entity id would buy a vanilla spawn egg and cost a slot
 * out of a space every mod in a large pack is competing for; the egg is a small item of our own
 * instead, which also gets to look like the thing it spawns.
 *
 * <p>
 * The tracking range matches the furthest the golem can be told to work, so a player standing at the
 * edge of its round still sees it. It is slow, so it is updated infrequently.
 *
 * <p>
 * The other edition calls {@code EntityRegistry.registerModEntity} from the mod's own init with a
 * name, a mod-scoped number and those three tracking values. 1.12.2 keeps entities in a registry
 * like everything else, so the same five facts are said to a builder and handed to the registry when
 * it asks - and the number, which was the mod's own id there, is the network id here and means the
 * same thing: which of this mod's entities a spawn packet is about.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class ModEntities {

    /** The golem's registry name, and the string a save records it under. */
    public static final ResourceLocation GOLEM_OF_WAYS = new ResourceLocation(Trmt.MODID, "golem_of_ways");

    private ModEntities() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry()
            .register(
                EntityEntryBuilder.<EntityGolemOfWays>create()
                    .entity(EntityGolemOfWays.class)
                    .id(GOLEM_OF_WAYS, 0)
                    .name(Trmt.MODID + ".golem_of_ways")
                    .tracker(80, 3, true)
                    .build());
        Trmt.LOG.info("Registered the Golem of Ways");
    }
}
