package com.trmtgtnh.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.trmtgtnh.Trmt;

/**
 * The condition each of this mod's advancement files loads on, {@code trmtgtnh:settings} (0.9.228, BP30 and BP33; Xep:
 * "Match 1.7.10").
 *
 * <p>
 * 1.7.10 registers an achievement only where a player can reach it: none with general.achievements off, a mode's and
 * its book's only with that mode switched on, the golem's only with the golem on and All Ways only with its upgrades
 * on too. Here the sixteen are data files, and until 0.9.228 every one of them loaded whatever the switches said, so
 * the tab stood with nothing to earn or with lines nobody could earn. Forge for this version reads a mod's advancement
 * file's {@code conditions} as it loads it ({@code ForgeHooks.loadAdvancements} hands them to
 * {@code CraftingHelper.processConditions}, read in its bytecode) and skips a file whose conditions fail - so each
 * file names the switches it needs, and this answers whether all of them are on, as
 * {@link ModAchievements#loads(String)} does for the file by name. Read at data load, as the server starts; a world's
 * earned ones stay in its save either way.
 *
 * <p>
 * Registered by the mod's own {@code assets/trmtgtnh/advancements/_factories.json}, under {@code "conditions"}, and
 * not from code. {@code ForgeHooks.loadAdvancements}, which loads every mod's advancements each time a server starts
 * or reloads them, begins with {@code CraftingHelper.init()}, which clears every registered condition, and then reads
 * each mod's factories file from its advancements folder with the conditions loader alone (read in its bytecode; its
 * file walk skips names starting with an underscore, so the factories file is never read as an advancement). A
 * condition registered in pre-init was gone by then, and so was one in the recipes folder's factories file, which only
 * the recipe loader reads: every file failed with "Unknown condition type" and none loaded (0.9.228, found twice by the
 * matched part's advancements case). The source is the mod's own folder or jar either way, so a dev run and a built
 * jar read it alike.
 */
public final class AdvancementSettings implements IConditionFactory {

    /** The condition's name, as each file writes it: the mod's id and the factories file's key. */
    public static final ResourceLocation NAME = new ResourceLocation(Trmt.MODID, "settings");

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        JsonArray named = JsonUtils.getJsonArray(json, "settings");
        final List<String> settings = new ArrayList<String>();
        for (JsonElement each : named) {
            String setting = JsonUtils.getString(each, "setting");
            if (!ModAchievements.known(setting)) {
                throw new JsonSyntaxException("trmtgtnh:settings names no switch called " + setting);
            }
            settings.add(setting);
        }
        return () -> ModAchievements.allOn(settings);
    }
}
