package com.trmtgtnh.client.gui;

import net.minecraftforge.fml.client.config.GuiEditArray;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries;
import net.minecraftforge.fml.client.config.IConfigElement;

/**
 * A row in Forge's array editor that shows the mob its line names beside the line.
 *
 * <p>
 * The same idea as {@link BlockIconArrayEntry} and for the same reason: a list of entity names is a list of strings you
 * cannot check by looking at. From 0.9.228 it draws what the own screen's mob list page draws ({@link MobPicture}): the
 * mob itself, drawn small, where there is a world to make one in; else its spawn egg; else this mod's own generic egg -
 * so a mob a mod registered without an egg, which most of a large pack's are, no longer shows its name alone. A name
 * nothing answers to shows nothing.
 *
 * <p>
 * Forge's screen keeps its text rows, as the decisions have it: the number stays part of the line here
 * ({@code Villager:1.5}), and the own screen is where each row has a field of its own for it. A row Forge's editor adds
 * is made with this class as well at this version - its {@code addNewEntry} asks the setting's array entry class - and
 * its picture follows its text from the next frame ({@link RowPicture}), so a mob typed into a new row shows at once.
 */
public class MobIconArrayEntry extends GuiEditArrayEntries.StringEntry {

    /** The mob the row's line names, worked out again whenever the line changes. */
    private final RowPicture<MobPicture.Resolved> picture = new RowPicture<MobPicture.Resolved>(
        new RowPicture.Resolve<MobPicture.Resolved>() {

            @Override
            public MobPicture.Resolved of(String text) {
                return MobPicture.resolve(nameOf(text));
            }
        });

    public MobIconArrayEntry(GuiEditArray owningScreen, GuiEditArrayEntries owningEntryList,
        IConfigElement configElement, Object value) {
        super(owningScreen, owningEntryList, configElement, value);
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY,
        boolean isSelected, float partialTicks) {
        super.drawEntry(slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY, isSelected, partialTicks);

        String text = this.textFieldValue.getText();
        MobPicture.Resolved resolved = picture.of(text);
        if (!resolved.known()) return;
        MobPicture.draw(nameOf(text), resolved, listWidth / 4 - 18, y);
    }

    /** The name a line names its mob by: the line without its multiplier. */
    static String nameOf(String line) {
        return MobRows.nameOf(line);
    }
}
