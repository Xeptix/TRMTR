package com.trmtgtnh.api;

import java.util.List;

/**
 * TRMTR itself, from {@link TrmtrApi#get()}.
 */
@ImplementedByTrmtr
public interface Trmtr {

    /** The API version TRMTR carries. */
    String version();

    /** Whether the API TRMTR carries is at least this one: how a mod built against a later minor asks. */
    boolean atLeast(int major, int minor);

    /** The newest data format TRMTR reads. */
    int format();

    /** Every family's key, in TRMTR's order. */
    List<String> families();

    /** Reading families, chains and wear. */
    Queries queries();

    /** Changing the ground. */
    Actions actions();
}
