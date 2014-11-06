/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

package com.oracle.truffle.r.nodes.builtin.base;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RInvisibleBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RBuiltinKind;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

@RBuiltin(name = "levels<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"x", ""})
// 2nd parameter is "value", but should not be matched against, so ""
public abstract class UpdateLevels extends RInvisibleBuiltinNode {

    @Specialization
    @TruffleBoundary
    protected RAbstractVector updateLevels(RAbstractVector vector, @SuppressWarnings("unused") RNull levels) {
        controlVisibility();
        RVector v = vector.materialize();
        v.setLevels(null);
        return v;
    }

    @Specialization
    @TruffleBoundary
    protected RAbstractVector updateLevels(RAbstractVector vector, Object levels) {
        controlVisibility();
        RVector v = vector.materialize();
        v.setLevels(levels);
        return v;
    }

    @Specialization
    @TruffleBoundary
    protected RFactor updateLevels(RFactor factor, @SuppressWarnings("unused") RNull levels) {
        controlVisibility();
        factor.getVector().setLevels(null);
        return factor;
    }

    @Specialization
    @TruffleBoundary
    protected RFactor updateLevels(RFactor factor, Object levels) {
        controlVisibility();
        factor.getVector().setLevels(levels);
        return factor;
    }

}
