package com.trmtgtnh.client.gui;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

/**
 * A row in the settings screen that is a button rather than a setting.
 *
 * <p>
 * Both older editions put two buttons in a row of their own at the foot of the config screen - offer
 * these settings to the server, and open the Wear Table - by rebuilding Forge's screen against a
 * height two rows shorter. Cloth owns its own footer and there is no such seam, so the two become
 * entries at the top of the first category: a smaller intrusion than taking the footer over, and one
 * that scrolls with everything else.
 *
 * <p>
 * It holds no value, which is the whole of why it is short: nothing to save, nothing to reset, and a
 * default of nothing. Cloth asks for all three because an entry usually edits something.
 */
public final class ActionEntry extends TooltipListEntry<Void> {

    private final Button button;

    public ActionEntry(Component label, final Runnable act) {
        super(label, null);
        this.button = new Button(0, 0, 150, 20, label, pressed -> act.run());
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void save() {}

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.singletonList(button);
    }

    @Override
    public void render(PoseStack pose, int index, int y, int x, int width, int height, int mouseX, int mouseY,
        boolean hovered, float delta) {
        super.render(pose, index, y, x, width, height, mouseX, mouseY, hovered, delta);
        // Across the row rather than at a fixed width: this list is as wide as the window and a
        // button pinned to a hundred and fifty pixels in the middle of it reads as a mistake.
        button.setWidth(Math.min(200, width));
        button.x = x + (width - button.getWidth()) / 2;
        button.y = y;
        button.render(pose, mouseX, mouseY, delta);
    }
}
