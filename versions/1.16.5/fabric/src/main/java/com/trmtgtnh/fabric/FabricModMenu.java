package com.trmtgtnh.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.trmtgtnh.client.gui.ConfigScreens;

/**
 * Where Mod Menu's Config button goes.
 *
 * <p>
 * The Fabric half of what Forge answers with an extension point. There is no mod list with a Config
 * button on this loader, so Mod Menu is the only mod list a player reaches a settings screen from -
 * which is why this is written against it and why it is optional: this class is looked at by Mod
 * Menu and by nothing else, and Mod Menu is not there to look when it is absent. The key, /trmt config
 * and the Options screen's button reach the settings without it (0.9.228).
 *
 * <p>
 * The screen is chosen by {@code ConfigScreens}, shared with the other loader. Until 0.9.228 this named
 * {@code ConfigScreen}, built on Cloth Config, which this edition required; Cloth is optional from then
 * (decided), so this class names no Cloth class at all and {@code ConfigScreens.open} decides which screen
 * opens - the mod's own by default, Cloth's on the setting where Cloth is loaded.
 */
public final class FabricModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::open;
    }
}
