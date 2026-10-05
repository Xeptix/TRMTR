package com.trmtgtnh.util;

/**
 * Which blocks the inspection readout asks about and speaks for, decided without the game.
 *
 * <p>
 * Both failures this pins were silent. Waila was handed only the ghost classes, so reinforcement and
 * wards on ground nobody had worn - which is most of what gets warded - never reached the tooltip. And
 * the crosshair asked the server about a block that was not a ghost only while reinforcement was on,
 * so a client running wards without reinforcement never asked about an unworn floor. Neither said a
 * word; the lines were simply absent.
 */
public final class InspectionReach {

    private InspectionReach() {}

    /**
     * Whether the block under the crosshair is worth asking the server about.
     *
     * <p>
     * Nothing is asked while nothing reads the answer: Waila's provider is the only reader, and without
     * it every block a player looked at would cost a request a second for a reply nobody sees. Worn
     * ground always is worth asking about. Anything else only while a feature that can sit on a block
     * without wearing it is switched on, because a reinforcement or a ward is all the reply can carry
     * for a block that shows nothing. The server stays silent about a position with no record, so a
     * plain block costs a request a second while it is looked at, and nothing back.
     *
     * @param readerPresent true when something on this client reads the reply - Waila is installed
     */
    public static boolean asks(boolean readerPresent, boolean ghost, boolean reinforceEnabled, boolean wardEnabled) {
        return readerPresent && (ghost || reinforceEnabled || wardEnabled);
    }

    /**
     * Whether one of the two registered Waila providers writes lines for this block.
     *
     * <p>
     * One is registered against every block and one against the ghost classes, and exactly one may
     * speak for any block whichever way Waila matches a provider to a block. Matched on isInstance, a
     * ghost reaches both and only the ghost provider speaks. Matched on the exact class, a ghost reaches
     * only its own provider, which still speaks, so nothing a ghost showed before can be lost.
     *
     * @param everyBlock true for the provider registered against every block
     * @param ghost      true when the block is one of this mod's ghosts
     */
    public static boolean speaks(boolean everyBlock, boolean ghost) {
        return everyBlock != ghost;
    }

    /**
     * Whether a reinforcement's worth against traffic is said of this block.
     *
     * <p>
     * Only of ground that wears through gradations: a ghost, or a block the server gives a run of steps to. A
     * reinforced pane of glass has no run and never wears, and a reinforced leaf or plant has none either and
     * is not trampled at all - a protected tally is refused outright rather than made dearer - so promising
     * either more lifetimes of traffic would describe wear it never sees. Unworn ground with a run is told it,
     * because since 0.9.213 its first footstep draws the threshold a reinforcement multiplies, where it used to
     * keep a stand-in of one that made it wear sooner rather than later.
     *
     * @param onARun true when the server's last reply gave the block standing there a run longer than nought
     */
    public static boolean wears(boolean ghost, boolean onARun) {
        return ghost || onARun;
    }
}
