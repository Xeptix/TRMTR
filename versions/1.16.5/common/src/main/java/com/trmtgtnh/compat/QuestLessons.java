package com.trmtgtnh.compat;

/**
 * Which of the three enchantments the quest chapter can honestly teach, and the sentences of the chapter that
 * change with them.
 *
 * <p>
 * A lesson is taught only where its switch is on and its enchantment holds an id, and neither answers that alone.
 * The id is claimed whatever the switch says, so that a tool already carrying the enchantment does not turn into a
 * broken one when somebody turns the feature off, which means an id proves only that a number was reserved; and a
 * switch that is on proves nothing where registration found no free id. The recipes, the chest loot and the
 * achievements have always asked both. The chapter asked only for the id, so with a switch off it went on writing a
 * quest for a book nothing in the pack makes, put that lesson on the finale's prize, and told the player the
 * Wayfarer takes all three.
 *
 * <p>
 * Kept apart from the rest of the chapter because these are the only parts of it that depend on the three
 * switches, and away from the game a test can go through every combination of them.
 */
final class QuestLessons {

    /** How a lesson the pack cannot teach is written, in the stamp and wherever an id is asked for. */
    static final int NOT_TAUGHT = -1;

    private final int reinforce;

    private final int ward;

    private final int light;

    /** Each argument the id that lesson is taught at, or anything below nought where it is not taught. */
    QuestLessons(int reinforce, int ward, int light) {
        this.reinforce = reinforce < 0 ? NOT_TAUGHT : reinforce;
        this.ward = ward < 0 ? NOT_TAUGHT : ward;
        this.light = light < 0 ? NOT_TAUGHT : light;
    }

    /** The enchantment's own id where its switch is on and it found one. Nought is a real id. */
    static int taught(boolean switchedOn, int effectId) {
        return switchedOn && effectId >= 0 ? effectId : NOT_TAUGHT;
    }

    int reinforce() {
        return reinforce;
    }

    int ward() {
        return ward;
    }

    int light() {
        return light;
    }

    int count() {
        return (reinforce >= 0 ? 1 : 0) + (ward >= 0 ? 1 : 0) + (light >= 0 ? 1 : 0);
    }

    /**
     * What the finale's Wayfarer carries: every lesson taught and no other, in the order of their quests' numbers -
     * reinforcing, warding, wayfinding.
     */
    int[] prize() {
        int[] ids = new int[count()];
        int at = 0;
        if (reinforce >= 0) ids[at++] = reinforce;
        if (ward >= 0) ids[at++] = ward;
        if (light >= 0) ids[at++] = light;
        return ids;
    }

    /**
     * The lessons' part of the chapter's stamp: one number a lesson, carrying its id and its switch together.
     *
     * <p>
     * A lesson not taught is written as minus one whatever id it holds. Nothing is lost by that, because nothing the
     * chapter writes depends on the id of a lesson it does not teach. With all three taught it is the same three ids
     * in the same places the stamp has always carried, so a pack with every switch on sees only the version change.
     */
    String stamp() {
        return "/" + reinforce + "/" + ward + "/" + light;
    }

    /**
     * The chunk tamper quest's closing paragraph.
     *
     * <p>
     * It used to say "the three enchantments below", wrong on a pack teaching fewer and wrong about where they are
     * drawn, which is the row above; the direction goes, since the questbook's own lines show it. With no lesson
     * taught only the enchantments leave the sentence, because the golem and the Wayfarer still hang off this quest.
     */
    String chunkTamperCloses() {
        switch (count()) {
            case 3:
                return "\n\nIt is also the tool the three enchantments hang off, so this is the quest that opens the rest of the chapter.";
            case 2:
                return "\n\nIt is also the tool the two enchantments hang off, so this is the quest that opens the rest of the chapter.";
            case 1:
                return "\n\nIt is also the tool the enchantment hangs off, so this is the quest that opens the rest of the chapter.";
            default:
                return "\n\nThis is also the quest that opens the rest of the chapter.";
        }
    }

    /** The Wayfarer quest's first sentence, naming as many lessons as there are and never one more. */
    String wayfarerOpens() {
        switch (count()) {
            case 3:
                return "The last tamper. It never wears out, it mends without spending material, and it takes all three lessons at once - each still comes as a book to put on it.";
            case 2:
                return "The last tamper. It never wears out, it mends without spending material, and it takes both lessons at once - each still comes as a book to put on it.";
            case 1:
                return "The last tamper. It never wears out, it mends without spending material, and it takes the "
                    + onlyLesson()
                    + " lesson as well - which still comes as a book to put on it.";
            default:
                return "The last tamper. It never wears out and it mends without spending material.";
        }
    }

    /** The one lesson taught, by the name its book's tooltip shows. Asked only where exactly one is. */
    private String onlyLesson() {
        if (reinforce >= 0) return "reinforcing";
        if (ward >= 0) return "warding";
        return "wayfinding";
    }
}
