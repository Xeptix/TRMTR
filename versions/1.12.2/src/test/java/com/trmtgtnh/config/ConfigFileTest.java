package com.trmtgtnh.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mod's own settings reader, checked against bytes Forge actually wrote.
 *
 * <p>
 * The point of owning this format is that the file survives the ports: Forge's {@code Configuration}
 * is gone after 1.12.2 and Fabric never had a config system at all, so a mod wanting one settings
 * file across every edition has to read it itself. What would make that worthless is the two parsers
 * disagreeing, so the fixtures here are not written by hand - they are slices of this mod's own
 * 326-kilobyte {@code trmtgtnh.cfg}, exactly as Forge's writer produced it, CRLF line endings and
 * all.
 *
 * <p>
 * <strong>Forge's parser was tried as a live oracle first and cannot be used.</strong> Its
 * {@code Configuration} constructor throws a {@link NullPointerException} outside a running game -
 * it reaches for FML's logging before it reads a byte - so a unit test cannot ask it what a file
 * means. Captured output is the honest substitute for the direction tested here, and the other
 * direction, Forge reading a file this class wrote, is proven in the join probe where FML is up.
 */
class ConfigFileTest {

    /** A real slice: banner, category comment, all four scalar types, five lists. */
    private static final String FORGE_WRITTEN = "forge-written.cfg";

    /** A real slice holding categories inside a category, which this mod's file has thirteen of. */
    private static final String FORGE_NESTED = "forge-nested.cfg";

    private static File fixture(File folder, String name) throws Exception {
        InputStream in = ConfigFileTest.class.getResourceAsStream(name);
        assertNotNull(in, "the fixture " + name + " is missing from the test resources");
        File out = new File(folder, "trmtgtnh.cfg");
        Files.copy(in, out.toPath());
        in.close();
        return out;
    }

    @Test
    void readsTheScalarsForgeWrote(@TempDir File folder) throws Exception {
        ConfigFile mine = new ConfigFile(fixture(folder, FORGE_WRITTEN));
        mine.load();

        // Values taken from the fixture, which is the shipped default file.
        assertEquals(true, mine.getBoolean("autoDetect", "surfaces", false, "x"), "a boolean");
        assertEquals(true, mine.getBoolean("groundCoverAutoDetect", "surfaces", false, "x"), "a boolean after a list");
    }

    @Test
    void readsAListForgeWrote(@TempDir File folder) throws Exception {
        ConfigFile mine = new ConfigFile(fixture(folder, FORGE_WRITTEN));
        mine.load();

        String[] excluded = mine.get("surfaces", "exclude", new String[0], "x")
            .getStringList();

        assertTrue(excluded.length >= 5, "the list came back with " + excluded.length + " items");
        assertEquals("minecraft:mycelium", excluded[0], "the first item");
        assertTrue(
            java.util.Arrays.asList(excluded)
                .contains("minecraft:farmland"),
            "an item from the middle of the list");
    }

    @Test
    void readsCategoriesInsideCategories(@TempDir File folder) throws Exception {
        ConfigFile mine = new ConfigFile(fixture(folder, FORGE_NESTED));
        mine.load();

        assertTrue(
            mine.getCategoryNames()
                .contains("families.grass"),
            "a nested category should be named by its full path, saw " + mine.getCategoryNames());
        ConfigFile.Category grass = mine.getCategory("families.grass");
        assertTrue(
            grass.keySet()
                .size() > 0,
            "the nested category should hold settings");
    }

    /**
     * Every name and value survives being read and written again.
     *
     * <p>
     * The formatting may differ - help text can wrap elsewhere - but not one name and not one value.
     * This is the property a player relies on when they move the file between editions, and the one
     * that would quietly rot if only the reader were tested.
     */
    @Test
    void whatIsReadIsWrittenBack(@TempDir File folder) throws Exception {
        for (String name : new String[] { FORGE_WRITTEN, FORGE_NESTED }) {
            File own = new File(folder, name.replace('.', '_'));
            assertTrue(own.mkdirs(), "could not make a folder for " + name);
            File path = fixture(own, name);

            ConfigFile first = new ConfigFile(path);
            first.load();
            String before = inventory(first);

            first.save();

            ConfigFile second = new ConfigFile(path);
            second.load();

            assertEquals(before, inventory(second), "a round trip changed something in " + name);
        }
    }

    /** Twice more, because a second write is where a parser's own output trips it up. */
    @Test
    void threeRoundTripsChangeNothing(@TempDir File folder) throws Exception {
        File path = fixture(folder, FORGE_NESTED);
        ConfigFile mine = new ConfigFile(path);
        mine.load();
        String first = inventory(mine);

        for (int round = 0; round < 3; round++) {
            ConfigFile again = new ConfigFile(path);
            again.load();
            again.save();
            ConfigFile check = new ConfigFile(path);
            check.load();
            assertEquals(first, inventory(check), "round " + round + " changed something");
        }
    }

    /** Every category, name and value, sorted, with comments deliberately left out. */
    private static String inventory(ConfigFile config) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String name : config.getCategoryNames()) {
            for (ConfigFile.Setting setting : config.getCategory(name)
                .getValues()
                .values()) {
                out.add(
                    name + "."
                        + setting.getName()
                        + "="
                        + (setting.isList() ? String.join(",", setting.getStringList()) : setting.getString()));
            }
        }
        java.util.Collections.sort(out);
        return String.join("\n", out);
    }

    /** A missing file leaves every default in place and asks to be written. */
    @Test
    void aMissingFileIsNotAnError(@TempDir File folder) {
        ConfigFile mine = new ConfigFile(new File(folder, "nothing-here.cfg"));
        mine.load();

        assertTrue(mine.hasChanged(), "a file that does not exist wants writing");
        assertEquals(true, mine.getBoolean("aSwitch", "general", true, "x"), "the default stands");
    }

    /** A line nobody can parse costs that line and nothing else. */
    @Test
    void rubbishInTheFileIsSurvivable(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        Files.write(
            path.toPath(),
            java.util.Arrays.asList(
                "# Configuration file",
                "",
                "general {",
                "    B:good=false",
                "    this line is not a setting at all",
                "    I:alsoGood=12",
                "}"),
            StandardCharsets.UTF_8);

        ConfigFile mine = new ConfigFile(path);
        mine.load();

        assertEquals(false, mine.getBoolean("good", "general", true, "x"), "before the bad line");
        assertEquals(12, mine.getInt("alsoGood", "general", 1, 0, 100, "x"), "after the bad line");
    }

    /**
     * A number outside its range is left alone, which is what Forge does with one.
     *
     * <p>
     * Worth stating plainly, because this test used to assert the opposite and say "as Forge does"
     * while doing it. Forge's bounded getter calls setMinValue and setMaxValue, which the config
     * screen reads for its sliders and its tooltips, and then replaces the held value only when it
     * will not parse: {@code Property.isIntValue} is a bare {@code Integer.parseInt}. A figure a
     * text editor put above the maximum therefore reaches the mod as written, on 1.7.10 and so here.
     */
    @Test
    void anOutOfRangeNumberSurvives(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        Files.write(
            path.toPath(),
            java.util.Arrays.asList("# Configuration file", "", "general {", "    I:tooBig=5000", "}"),
            StandardCharsets.UTF_8);

        ConfigFile mine = new ConfigFile(path);
        mine.load();

        assertEquals(5000, mine.getInt("tooBig", "general", 10, 0, 100, "x"), "passed through as written");
        assertTrue(
            mine.getCategory("general")
                .get("tooBig")
                .isBounded(),
            "and the screen still knows the range");
    }

    /** What will not parse at all is replaced by the default, which is the one repair Forge makes. */
    @Test
    void somethingThatIsNotANumberFallsBackToTheDefault(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        Files.write(
            path.toPath(),
            java.util.Arrays.asList("# Configuration file", "", "general {", "    I:notANumber=lots", "}"),
            StandardCharsets.UTF_8);

        ConfigFile mine = new ConfigFile(path);
        mine.load();

        assertEquals(10, mine.getInt("notANumber", "general", 10, 0, 100, "x"), "the default");
    }

    /**
     * An edited setting marks the file as needing writing.
     *
     * <p>
     * The reason this test exists: it did not, and the only caller that writes the file asks first.
     * Every edit the mod makes after load - a family's block list from the wear editor, a mob
     * multiplier, a restored snapshot - goes through {@code Setting.set} and went nowhere. It would
     * have looked like it worked for the whole session and been gone at the next start.
     */
    @Test
    void anEditedSettingMarksTheFileChanged(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        ConfigFile mine = new ConfigFile(path);
        mine.load();
        mine.getBoolean("enabled", "general", true, "x");
        mine.save();
        assertFalse(mine.hasChanged(), "nothing to write just after writing");

        mine.getCategory("general")
            .get("enabled")
            .set(false);
        assertTrue(mine.hasChanged(), "an edit is something to write");

        mine.save();
        assertFalse(mine.hasChanged(), "and writing clears it");
        assertTrue(
            new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8).contains("B:enabled=false"),
            "and the edit is in the file");
    }

    /** A list set to the same contents is not a change, so closing a screen does not force a write. */
    @Test
    void settingTheSameValueIsNotAChange(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        ConfigFile mine = new ConfigFile(path);
        mine.load();
        mine.get("general", "blocks", new String[] { "minecraft:grass", "minecraft:dirt" }, "x");
        mine.getBoolean("enabled", "general", true, "x");
        mine.save();

        mine.getCategory("general")
            .get("blocks")
            .set(new String[] { "minecraft:grass", "minecraft:dirt" });
        mine.getCategory("general")
            .get("enabled")
            .set(true);
        assertFalse(mine.hasChanged(), "the same values are not an edit");
    }

    /**
     * A bad entry in a list of numbers is skipped, not turned into a nought.
     *
     * <p>
     * Here because of what a nought means to the one setting that uses this: the dimension list,
     * where nought is the Overworld. A typo would have listed the dimension almost everybody is
     * standing in, and in whitelist mode that is the whole difference between erosion everywhere and
     * erosion nowhere. The cause was arithmetic rather than API - an array subscript that increments
     * before the parse it feeds - which is the one kind of mistake nothing in this build can catch
     * except a test that counts what came back.
     */
    @Test
    void aBadNumberInAListIsSkippedRatherThanZeroed(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        Files.write(
            path.toPath(),
            java.util.Arrays.asList(
                "# Configuration file",
                "",
                "general {",
                "    I:dimensionList <",
                "        -1",
                "        theNether",
                "        1",
                "     >",
                "}"),
            StandardCharsets.UTF_8);

        ConfigFile mine = new ConfigFile(path);
        mine.load();

        assertArrayEquals(
            new int[] { -1, 1 },
            mine.get("general", "dimensionList", new int[0], "x")
                .getIntList(),
            "the Nether and the End, and no Overworld conjured out of the typo");
    }

    /** A setting keeps the code's default, which is what the screen's reset button resets to. */
    @Test
    void aSettingKeepsTheCodesDefault(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        Files.write(
            path.toPath(),
            java.util.Arrays.asList("# Configuration file", "", "general {", "    I:gradations=24", "}"),
            StandardCharsets.UTF_8);

        ConfigFile mine = new ConfigFile(path);
        mine.load();
        assertEquals(24, mine.getInt("gradations", "general", 80, 16, 256, "x"), "the file wins");
        assertEquals(
            "80",
            mine.getCategory("general")
                .get("gradations")
                .getDefault(),
            "but the default is remembered");
    }

    /** CRLF, because that is what Forge writes on Windows and what the real file has. */
    @Test
    void windowsLineEndingsAreRead(@TempDir File folder) throws Exception {
        File path = new File(folder, "trmtgtnh.cfg");
        String body = "# Configuration file\r\n\r\ngeneral {\r\n    B:switched=true\r\n    S:list <\r\n"
            + "        one\r\n        two\r\n     >\r\n}\r\n";
        Files.write(path.toPath(), body.getBytes(StandardCharsets.UTF_8));

        ConfigFile mine = new ConfigFile(path);
        mine.load();

        assertEquals(true, mine.getBoolean("switched", "general", false, "x"));
        assertArrayEquals(
            new String[] { "one", "two" },
            mine.get("general", "list", new String[0], "x")
                .getStringList());
    }

    /**
     * A nested category is written even when nobody asked for its parent.
     *
     * <p>
     * The case the probe caught. A category is written inside its parent, so one whose parent was
     * never created has nothing to be written inside and vanished on save - and this mod asks for
     * thirteen nested categories without naming the parent of a single one.
     */
    @Test
    void aNestedCategorySurvivesWithoutItsParentBeingAskedFor(@TempDir File folder) {
        File path = new File(folder, "trmtgtnh.cfg");
        ConfigFile writing = new ConfigFile(path);
        writing.load();
        writing.getBoolean("deeper", "families.grass", false, "Nested, with no families asked for.");
        writing.save();

        ConfigFile reading = new ConfigFile(path);
        reading.load();

        assertEquals(false, reading.getBoolean("deeper", "families.grass", true, "x"), "the nested setting");
        assertTrue(
            reading.getCategoryNames()
                .contains("families"),
            "the parent should have been made implicitly, saw " + reading.getCategoryNames());
    }
}
