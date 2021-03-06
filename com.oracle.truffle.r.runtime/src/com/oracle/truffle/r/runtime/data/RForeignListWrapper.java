/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractListVector;
import com.oracle.truffle.r.runtime.data.nodes.FastPathVectorAccess.FastPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.SlowPathVectorAccess.SlowPathFromListAccess;
import com.oracle.truffle.r.runtime.data.nodes.VectorAccess;
import com.oracle.truffle.r.runtime.interop.Foreign2R;

public final class RForeignListWrapper extends RAbstractListVector implements RForeignVectorWrapper {

    protected final TruffleObject delegate;

    public RForeignListWrapper(TruffleObject delegate) {
        super(RDataFactory.INCOMPLETE_VECTOR);
        this.delegate = delegate;
    }

    @Override
    public int getLength() {
        return RRuntime.getForeignArraySize(delegate, getInterop());
    }

    @Override
    public Object getInternalStore() {
        return delegate;
    }

    @Override
    public RList materialize() {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    @TruffleBoundary
    public Object getDataAtAsObject(int index) {
        return getDataAt(index);
    }

    @Override
    @TruffleBoundary
    public Object getDataAt(int index) {
        try {
            return Foreign2R.getUncached().convert((getInterop().readArrayElement(delegate, index)));
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw RInternalError.shouldNotReachHere(e);
        }
    }

    private static InteropLibrary getInterop() {
        return InteropLibrary.getFactory().getUncached();
    }

    private static final class FastPathAccess extends FastPathFromListAccess {

        @Child private InteropLibrary delegateInterop;
        @Child private Foreign2R foreign2r = Foreign2R.create();

        FastPathAccess(RAbstractContainer value) {
            super(value);
            delegateInterop = InteropLibrary.getFactory().create(((RForeignListWrapper) value).delegate);
        }

        @Override
        public boolean supports(Object value) {
            return super.supports(value) && delegateInterop.accepts(value);
        }

        @Override
        public RType getType() {
            return RType.List;
        }

        @Override
        protected int getLength(RAbstractContainer vector) {
            return RRuntime.getForeignArraySize(((RForeignListWrapper) vector).delegate, delegateInterop);
        }

        @Override
        protected Object getListElementImpl(AccessIterator accessIter, int index) {
            try {
                return foreign2r.convert(delegateInterop.readArrayElement(accessIter.getStore(), index));
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public VectorAccess access() {
        return new FastPathAccess(this);
    }

    private static final SlowPathFromListAccess SLOW_PATH_ACCESS = new SlowPathFromListAccess() {

        @Override
        public RType getType() {
            return RType.List;
        }

        @Override
        @TruffleBoundary
        protected int getLength(RAbstractContainer vector) {
            return RRuntime.getForeignArraySize(((RForeignListWrapper) vector).delegate, getInterop());
        }

        @Override
        @TruffleBoundary
        protected Object getListElementImpl(AccessIterator accessIter, int index) {
            RForeignListWrapper vector = (RForeignListWrapper) accessIter.getStore();
            try {
                return getInterop().readArrayElement(vector.delegate, index);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
        }
    };

    @Override
    public VectorAccess slowPathAccess() {
        return SLOW_PATH_ACCESS;
    }
}
