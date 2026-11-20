package com.trmtgtnh.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface TRMTR implements. A mod calls it and never implements it.
 *
 * <p>
 * An interface marked so may gain methods in a minor version of the API, which a mod's own implementation would not
 * have. The interfaces a mod does implement ({@link TrmtrPlugin}, {@link WearListener}, {@link GroundRule},
 * {@link WearerRule}, {@link WearRenderer}) only ever gain {@code default} methods.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ImplementedByTrmtr {}
