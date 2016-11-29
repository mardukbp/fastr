/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime.rng;

public abstract class RNGInitAdapter implements RandomNumberGenerator {

    protected static final double I2_32M1 = 2.3283064365386963e-10;

    // TODO: it seems like GNU R this is shared between the generators (does it matter?)
    protected final int[] iSeed = new int[625];

    @Override
    public void setISeed(int[] seeds) {
        for (int i = 1; i <= getNSeed(); i++) {
            iSeed[i - 1] = seeds[i];
        }
        fixupSeeds(false);
    }

    /**
     * Ensure 0 and 1 are never returned from rand generation algorithm.
     */
    protected static double fixup(double x) {
        /* transcribed from GNU R, RNG.c (fixup) */
        if (x <= 0.0) {
            return 0.5 * I2_32M1;
        }
        // removed fixup for 1.0 since x is in [0,1).
        // TODO Since GnuR does include this is should we not for compatibility (probably).
        // if ((1.0 - x) <= 0.0) return 1.0 - 0.5 * I2_32M1;
        return x;
    }
}
