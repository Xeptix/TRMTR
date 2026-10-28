package com.trmtgtnh.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Forge's settings file, keeping each heading's settings in the order the code declares them (0.9.228; Xep: "Declared
 * order everywhere").
 *
 * <p>
 * Forge 1.7.10 keeps a heading's settings in a sorted map unless it is given an order, so every page of this edition's
 * settings screens - and its file - listed them alphabetically (Depth, Healing, Phases, Quality, Wear), where every
 * port
 * lists them as declared (Quality, Wear, Healing, Phases, Depth), the order they were written to be read in. Every
 * typed
 * {@code get} of Forge's funnels into the two that take a {@link Property.Type}, so noting each key there sees every
 * declaration, in order; {@link #applyOrder} then hands that order to Forge, heading by heading, for its screen and the
 * file it writes alike. A setting the file holds that nothing declares stays after them, as Forge places it.
 */
public final class OrderedConfiguration extends Configuration {

    /** Every heading's keys as declared, in order; a heading's name as Forge files it. */
    private final Map<String, LinkedHashSet<String>> declared = new LinkedHashMap<String, LinkedHashSet<String>>();

    public OrderedConfiguration(File file) {
        super(file);
    }

    @Override
    public Property get(String category, String key, String defaultValue, String comment, Property.Type type) {
        note(category, key);
        return super.get(category, key, defaultValue, comment, type);
    }

    @Override
    public Property get(String category, String key, String[] defaultValues, String comment, Property.Type type) {
        note(category, key);
        return super.get(category, key, defaultValues, comment, type);
    }

    private void note(String category, String key) {
        String heading = getCategory(category).getQualifiedName();
        LinkedHashSet<String> keys = declared.get(heading);
        if (keys == null) {
            keys = new LinkedHashSet<String>();
            declared.put(heading, keys);
        }
        keys.add(key);
    }

    /**
     * Gives Forge every heading's declared order, but for the headings named, which set their own (the multipliers
     * page's, spec 1 of step 5).
     */
    public void applyOrder(String... ownOrder) {
        Set<String> leave = new HashSet<String>();
        for (String heading : ownOrder) {
            leave.add(getCategory(heading).getQualifiedName());
        }
        for (Map.Entry<String, LinkedHashSet<String>> heading : declared.entrySet()) {
            if (leave.contains(heading.getKey())) continue;
            ConfigCategory category = getCategory(heading.getKey());
            category.setPropertyOrder(new ArrayList<String>(heading.getValue()));
        }
    }

    /** The keys declared under a heading, in order: for the test. */
    java.util.List<String> declaredIn(String category) {
        LinkedHashSet<String> keys = declared.get(getCategory(category).getQualifiedName());
        return keys == null ? Arrays.<String>asList() : new ArrayList<String>(keys);
    }
}
