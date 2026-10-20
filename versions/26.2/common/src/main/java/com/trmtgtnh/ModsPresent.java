package com.trmtgtnh;

/**
 * Whether another mod is installed, asked without naming a loader.
 *
 * <p>
 * The 1.12.2 edition has a class of this name holding one line - {@code Loader.isModLoaded(modId)} -
 * and it exists for a reason worth repeating: it was carved out of the settings layer so that three
 * thousand lines of settings could stop naming Forge. That was what let the settings come here at
 * all. This is the same seam, one step wider, because here the answer differs by loader.
 *
 * <p>
 * Done by handing the answer in rather than by looking it up. Architectury offers
 * {@code @ExpectPlatform}, which would read better at the call site, and it was not used: it would put
 * an Architectury annotation into the module every later port has to carry, and it cannot be tested
 * without a loader present. A field that each loader fills in is plain Java, is exercised by an
 * ordinary unit test, and leaves the next port free to choose differently.
 *
 * <p>
 * The cost of handing it in is that somebody has to, and a seam nobody wired would answer "no mod is
 * installed" to every question - which is not an error, just every integration quietly switched off.
 * That is the shape of bug this project has been bitten by twice, so the unwired state says so out
 * loud the first time it is asked, and goes quiet after, because a warning per block per frame is a
 * worse bug than the one it reports.
 */
public final class ModsPresent {

    /** What a loader module supplies: the loader's own answer to "is this installed". */
    public interface Check {

        boolean has(String modId);
    }

    private static volatile Check check;

    private static volatile boolean complained;

    private ModsPresent() {}

    /**
     * Tells this what to ask. Called once by each loader module as the mod starts.
     *
     * <p>
     * Last caller wins, deliberately: a test that installs its own answer and a loader that installed
     * the real one are both legitimate, and ordering between them is the caller's business rather than
     * something to be enforced from here.
     */
    public static void use(Check loaders) {
        check = loaders;
    }

    /** Forgets whatever was handed in. For tests, which must not leak an answer into the next one. */
    public static void forget() {
        check = null;
        complained = false;
    }

    /** Whether a loader has told this how to answer yet. */
    public static boolean wired() {
        return check != null;
    }

    /**
     * What is currently being asked, so a caller that replaces it can put it back.
     *
     * <p>
     * For the one caller that has to lie for the length of a call: the compat spike answers yes to
     * everything so that the two written integrations will actually write, and then has to restore
     * the loader's own answer. {@link #forget} is not enough for that - it leaves every integration
     * reporting absent for the rest of the session, which is a worse state than the one the spike
     * was checking.
     */
    public static Check current() {
        return check;
    }

    public static boolean has(String modId) {
        Check asking = check;
        if (asking == null) {
            if (!complained) {
                complained = true;
                Trmt.LOG.warn(
                    "Nothing has told this edition how to look for other mods, so every integration "
                        + "will report absent. The loader module should call ModsPresent.use() as it "
                        + "starts; this is said once.");
            }
            return false;
        }
        return asking.has(modId);
    }
}
