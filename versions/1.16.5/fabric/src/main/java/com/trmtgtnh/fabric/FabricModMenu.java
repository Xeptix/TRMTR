package com.trmtgtnh.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.trmtgtnh.client.gui.ConfigScreen;

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
 * The screen is {@code ConfigScreen}, shared with the other loader. Nothing here is more than the
 * one line that names it.
 */
public final class FabricModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::build;
    }
}
