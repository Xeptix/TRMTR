package com.trmtgtnh.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.trmtgtnh.client.gui.ConfigScreens;

/**
 * Where Mod Menu's Config button goes.
 *
 * <p>
 * The Fabric half of what Forge answers with an extension point. There is no mod list with a Config
 * button on this loader, so Mod Menu is the only place a player reaches a settings screen from -
 * which is why this is written against it and why it is optional: this class is looked at by Mod
 * Menu and by nothing else, and Mod Menu is not there to look when it is absent.
 *
 * <p>
 * The screen is chosen by {@code ConfigScreens}, shared with the other loaders. At 1.21.1 this named
 * {@code ConfigScreen}, built on Cloth Config, which that edition required; from 26.1.2 Cloth is optional
 * (decided), so this class names no Cloth class at all and {@code ConfigScreens.open} decides which screen
 * there is - Cloth's where Cloth is loaded, and the mod's own otherwise.
 */
public final class FabricModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::open;
    }
}
