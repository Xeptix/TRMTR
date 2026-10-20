package com.trmtgtnh.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import com.trmtgtnh.Trmt;

/**
 * Opening the golem's window, which is the one thing in this mod neither loader will let a shared
 * module do.
 *
 * <p>
 * This replaces {@code TrmtGuiHandler}, and the replacement is not a rename. Forge's
 * {@code IGuiHandler} addressed a screen by a block position; a golem has not got one, so both older
 * editions smuggled its entity id through the handler's {@code x} argument and looked the entity up
 * again on the far side. There is no such handler at this version. A window is a registry object
 * with a {@link MenuType}, the client is told which type to open by number, and anything else the
 * client needs - here, which golem - travels as extra bytes alongside.
 *
 * <p>
 * Writing those extra bytes is the part that cannot be shared. Forge wants
 * {@code NetworkHooks.openGui} with a buffer-writing callback and a menu type made by
 * {@code IForgeMenuType}; Fabric wants an {@code ExtendedScreenHandlerFactory} whose
 * {@code writeScreenOpeningData} does the same job from the other end. Both end up sending one
 * varint and reading it back; neither will accept the other's way of saying so.
 *
 * <p>
 * So this is the arrangement every loader-shaped question in this module is under - {@code
 * Client.Side}, {@code Plants}, {@code ModsPresent}, {@code TrmtNetwork.Channel}, {@code Fluids.Side}
 * - a settable interface, a {@link #wired()} anybody can ask, and a single warning the first time
 * somebody opens a golem on a loader that forgot to fill it in. Warning once rather than throwing
 * because a golem whose screen will not open is a bad afternoon, and a server that dies when
 * somebody sneak-clicks one is a worse one.
 *
 * <p>
 * The identity check stays on the server, exactly as the handler's javadoc said it should: the
 * client is sent a number, the server looks the entity up itself, and an id that is not a golem
 * opens nothing rather than somebody else's window.
 */
public final class GolemMenu {

    /** What a loader module supplies: a way to put this player in front of this golem. */
    public interface Opener {

        /**
         * @param player the player to show it to, always a server player in practice
         * @param golem  the golem whose window it is; its id is what has to reach the client
         */
        void open(Player player, EntityGolemOfWays golem);
    }

    /** The type, which each loader registers and fills in, because the two make them differently. */
    private static MenuType<ContainerGolem> type;

    private static Opener opener;

    private static boolean warned;

    private GolemMenu() {}

    /** Called by a loader module as it starts. */
    public static void use(Opener loaders) {
        opener = loaders;
    }

    /** Called by a loader module once it has made a menu type of the shape it needs. */
    public static void use(MenuType<ContainerGolem> loaders) {
        type = loaders;
    }

    /** Whether a loader has wired both halves of this up. */
    public static boolean wired() {
        return opener != null && type != null;
    }

    /** The registered type, or null before a loader has made one. */
    public static MenuType<ContainerGolem> type() {
        return type;
    }

    /** Shows this player this golem's orders, or says once that nothing can. */
    public static void open(Player player, EntityGolemOfWays golem) {
        if (player == null || golem == null) return;
        if (!wired()) {
            if (!warned) {
                warned = true;
                Trmt.LOG.warn(
                    "Somebody asked a Golem of Ways for its orders and no loader has wired GolemMenu up, "
                        + "so no window can be opened. The golem carries on working; this is said once.");
            }
            return;
        }
        opener.open(player, golem);
    }

    /**
     * The golem this id names, or null if it does not name one.
     *
     * <p>
     * Shared, because both loaders need it and the answer is the same on both: the id arrives from a
     * client, so it is checked rather than trusted. A forged number finds nothing or finds something
     * that is not a golem, and either way no window opens.
     */
    public static EntityGolemOfWays golemAt(net.minecraft.world.level.Level level, int entityId) {
        if (level == null) return null;
        net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
        return entity instanceof EntityGolemOfWays ? (EntityGolemOfWays) entity : null;
    }
}
