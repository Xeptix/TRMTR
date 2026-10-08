package com.trmtgtnh.core;

/**
 * Worn by a class only when this mod's mixins were applied to it - {@code MixinLibrarianBooks} declares it, so its
 * target, the librarian's enchanted-book trade, carries it in a game where {@code mixins.trmtgtnh.json} applied and
 * not otherwise.
 *
 * <p>
 * A positive answer to "did the mixins load?", because the obvious question has none: Mixin drops a config from
 * {@code Mixins.getConfigs()} as soon as it is selected, so by the time a mod could ask, a config that loaded and
 * one that never did look the same. The first version of {@code Trmt.mixinsLoaded} asked that way and warned in
 * every game. A common mixin carries it, so a dedicated server answers too.
 */
public interface MixinsApplied {
}
