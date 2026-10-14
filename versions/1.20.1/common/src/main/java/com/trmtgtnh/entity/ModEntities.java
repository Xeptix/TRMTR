package com.trmtgtnh.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;

/**
 * The mod's entities, and the seam that lets two loaders register them.
 *
 * <p>
 * Written rather than carried. The 1.12.2 edition is a Forge registry event handler and nothing
 * else; there is no such event in a module two loaders share, so this is the arrangement
 * {@code ModBlocks}, {@code ModItems}, {@code ModPotions} and {@code ModEnchantments} are already
 * under - the type is built here and handed to whatever the loader supplies for putting it away.
 *
 * <p>
 * Mod-scoped rather than global, which both older editions also chose and for the same reason: a
 * global entity id would buy a vanilla spawn egg and cost a slot out of a space every mod in a large
 * pack is competing for. The egg is a small item of our own instead, which also gets to look like
 * the thing it spawns.
 *
 * <h2>What the type now carries that the older editions said elsewhere</h2>
 *
 * <p>
 * Three facts moved onto the type and off the entity, and all three were previously said twice:
 *
 * <ul>
 * <li><strong>The hitbox.</strong> {@code setSize(1.4F, 2.7F)} in the constructor there, declared
 * once here. The 2.7 is the figure the model's vertical extents were drawn to fill, so the two have
 * to agree and now only one of them says it.
 * <li><strong>How far it is tracked, and how often.</strong> The other edition's
 * {@code tracker(80, 3, true)} is eighty <em>blocks</em>; this version counts chunks, so five. That
 * is the one number here a careless carry would have got wrong by a factor of sixteen - and in the
 * forgiving direction, which is worse, because a golem tracked for 1,280 blocks does not look
 * broken, it just quietly costs every player on the server packets about a thing none of them can
 * see.
 * <li><strong>Its attributes.</strong> They are supplied per type before any golem exists rather
 * than set on each one as it is built. The four numbers still live on the entity, in
 * {@link EntityGolemOfWays#createAttributes()}, because that is where the reasoning about them is;
 * this only hands them over.
 * </ul>
 *
 * <p>
 * The tracking range matches the furthest the golem can be told to work, so a player standing at the
 * edge of its round still sees it. It is slow, so it is updated infrequently.
 */
public final class ModEntities {

    /**
     * What a loader module supplies: somewhere to put an entity type, and somewhere to put the
     * attributes that go with it.
     *
     * <p>
     * Two methods rather than one because the two happen at different moments on both loaders - a
     * type is registered with everything else and attributes are wanted later, once every type
     * exists - and because the loaders disagree about the shape of the second. Forge is handed a
     * built {@code AttributeSupplier}; Fabric is handed the builder and does the building. Neither
     * difference can be expressed here, which is exactly what makes this a seam.
     */
    public interface Registrar {

        void entity(ResourceLocation name, EntityType<?> type);

        void attributes(EntityType<?> type, AttributeSupplier.Builder built);
    }

    /** The golem's registry name, and the string a save records it under. */
    public static final ResourceLocation GOLEM_OF_WAYS = new ResourceLocation(Trmt.MODID, "golem_of_ways");

    /**
     * Eighty blocks, in the unit this version counts in.
     *
     * <p>
     * Named rather than written as a bare 5 next to a bare 3, because the two numbers beside each
     * other in the builder are in different units and nothing in the call says so.
     */
    private static final int TRACKED_CHUNKS = 80 / 16;

    /** How many ticks between telling a watcher where it is. Slow, because it is. */
    private static final int UPDATE_EVERY = 3;

    private static EntityType<EntityGolemOfWays> golem;

    private ModEntities() {}

    /** The registered type, or null before registration. */
    public static EntityType<EntityGolemOfWays> golemType() {
        return golem;
    }

    /**
     * A golem in this level, ready to be placed and spawned.
     *
     * <p>
     * Here rather than at each of the four places one is built - the build shape, the egg, the
     * command's demonstration yard and the spike - because every one of them would otherwise have
     * to name the type, and a constructor called with the wrong type produces an entity that saves
     * and reloads as something else. One place knows it.
     */
    public static EntityGolemOfWays newGolem(Level level) {
        return golem == null ? null : golem.create(level);
    }

    /** Builds the type and hands it over, with its attributes. */
    public static void register(Registrar into) {
        if (golem == null) {
            golem = EntityType.Builder.<EntityGolemOfWays>of(EntityGolemOfWays::new, MobCategory.MISC)
                .sized(1.4F, 2.7F)
                .clientTrackingRange(TRACKED_CHUNKS)
                .updateInterval(UPDATE_EVERY)
                .build(GOLEM_OF_WAYS.toString());
        }
        into.entity(GOLEM_OF_WAYS, golem);
        into.attributes(golem, EntityGolemOfWays.createAttributes());
        Trmt.LOG.info("Registered the Golem of Ways");
    }
}
