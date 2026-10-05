package com.trmtgtnh.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

/**
 * What {@code MixinPackRepository} assumes about the class it is injected into.
 *
 * <p>
 * <strong>A mixin's assumptions are not checked by the compiler.</strong> {@code @Shadow} names a
 * field by a string; if that field is renamed, moved or retyped, nothing fails until the game starts,
 * and then it fails as a wall of mixin transformer output rather than as anything that names this
 * mod. That is a slow way to learn something reflection can answer in a millisecond - and tests here
 * have Minecraft on the classpath, so they can simply ask.
 *
 * <p>
 * This is cheap insurance for the one thing in the texture pipeline that cannot be verified by
 * compiling: the shape of somebody else's class.
 */
class PackRepositoryStillLooksLikeThisTest {

    @Test
    void the_sources_field_is_still_a_mutable_looking_set_of_sources_called_sources() throws Exception {
        Field sources = PackRepository.class.getDeclaredField("sources");
        assertNotNull(sources);

        assertEquals(
            Set.class,
            sources.getType(),
            "the mixin replaces this wholesale, so it has to be a Set");

        ParameterizedType generic = (ParameterizedType) sources.getGenericType();
        assertEquals(
            RepositorySource.class,
            generic.getActualTypeArguments()[0],
            "and it has to hold the thing the mixin adds");
    }

    @Test
    void the_constructor_the_mixin_targets_is_still_the_one_that_does_the_work() throws Exception {
        // Two of them, and the mixin injects into exactly this one. The other takes sources alone
        // and hands them here, so injecting into both would add this mod's pack twice.
        Constructor<?> primary =
            PackRepository.class.getDeclaredConstructor(Pack.PackConstructor.class, RepositorySource[].class);
        assertNotNull(primary);

        assertEquals(
            2,
            PackRepository.class.getDeclaredConstructors().length,
            "if a third constructor appears, work out whether it reaches the one the mixin targets "
                + "- if it does not, the wear textures will be missing wherever it is used");
    }

    @Test
    void a_repository_built_by_hand_keeps_what_it_was_given() {
        // Proves the field is populated the way the mixin expects rather than, say, left null until
        // something else fills it. Built with no sources at all, which is legal and is the smallest
        // thing that answers the question.
        PackRepository repository = new PackRepository(Pack::new);
        assertNotNull(repository.getAvailableIds(), "a fresh repository should answer rather than throw");
        assertTrue(repository.getAvailableIds().isEmpty(), "and with nothing in it, answer with nothing");
    }
}
