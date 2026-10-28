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
 * The screen is chosen by {@code ConfigScreens}, shared with the other loaders: this mod's own, or
 * Cloth Config's where Cloth is installed and {@code client.settingsScreen} asks for it. Until
 * 0.9.227 this named the Cloth screen directly, because this jar required Cloth; from 0.9.228 Cloth
 * is optional (decided), so this class names no Cloth class at all.
 */
public final class FabricModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreens::open;
    }
}
