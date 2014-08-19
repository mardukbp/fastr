/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.access;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.nodes.Node.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.nodes.access.AccessArrayNode.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCast.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCastFactory.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCast.OperatorConverterNode;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNode.CoerceVector;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNodeFactory.CoerceVectorFactory;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNode.SetMultiDimDataNode;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNodeFactory.SetMultiDimDataNodeFactory;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@SuppressWarnings("unused")
@NodeChildren({@NodeChild(value = "v", type = RNode.class), @NodeChild(value = "newValue", type = RNode.class), @NodeChild(value = "recursionLevel", type = RNode.class),
                @NodeChild(value = "positions", type = PositionsArrayNodeValue.class, executeWith = {"v", "newValue"}),
                @NodeChild(value = "vector", type = CoerceVector.class, executeWith = {"newValue", "v", "positions"})})
public abstract class UpdateArrayHelperNode extends RNode {

    private final boolean isSubset;

    private final NACheck elementNACheck = NACheck.create();
    private final NACheck posNACheck = NACheck.create();
    private final NACheck namesNACheck = NACheck.create();

    protected abstract RNode getVector();

    protected abstract RNode getNewValue();

    protected abstract Object executeUpdate(VirtualFrame frame, Object v, Object value, int recLevel, Object positions, Object vector);

    @Child private UpdateArrayHelperNode updateRecursive;
    @Child private CastComplexNode castComplex;
    @Child private CastDoubleNode castDouble;
    @Child private CastIntegerNode castInteger;
    @Child private CastStringNode castString;
    @Child private CoerceVector coerceVector;
    @Child private ArrayPositionCast castPosition;
    @Child private OperatorConverterNode operatorConverter;
    @Child private SetMultiDimDataNode setMultiDimData;

    public UpdateArrayHelperNode(boolean isSubset) {
        this.isSubset = isSubset;
    }

    public UpdateArrayHelperNode(UpdateArrayHelperNode other) {
        this.isSubset = other.isSubset;
    }

    private Object updateRecursive(VirtualFrame frame, Object v, Object value, Object vector, Object operand, int recLevel) {
        if (updateRecursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateRecursive = insert(UpdateArrayHelperNodeFactory.create(this.isSubset, null, null, null, null, null));
        }
        return executeUpdate(frame, v, value, recLevel, operand, vector);
    }

    private Object castComplex(VirtualFrame frame, Object operand) {
        if (castComplex == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castComplex = insert(CastComplexNodeFactory.create(null, true, true, false));
        }
        return castComplex.executeCast(frame, operand);
    }

    private Object castDouble(VirtualFrame frame, Object operand) {
        if (castDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castDouble = insert(CastDoubleNodeFactory.create(null, true, true, false));
        }
        return castDouble.executeCast(frame, operand);
    }

    private Object castInteger(VirtualFrame frame, Object operand) {
        if (castInteger == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castInteger = insert(CastIntegerNodeFactory.create(null, true, true, false));
        }
        return castInteger.executeCast(frame, operand);
    }

    private Object castString(VirtualFrame frame, Object operand) {
        if (castString == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castString = insert(CastStringNodeFactory.create(null, true, true, false, true));
        }
        return castString.executeCast(frame, operand);
    }

    private Object coerceVector(VirtualFrame frame, Object vector, Object value, Object operand) {
        if (coerceVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            coerceVector = insert(CoerceVectorFactory.create(null, null, null));
        }
        return coerceVector.executeEvaluated(frame, value, vector, operand);
    }

    private Object castPosition(VirtualFrame frame, Object vector, Object operand) {
        if (castPosition == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castPosition = insert(ArrayPositionCastFactory.create(0, 1, true, false, null, null, null));
        }
        return castPosition.executeArg(frame, operand, vector, operand);
    }

    private void initOperatorConvert() {
        if (operatorConverter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            operatorConverter = insert(OperatorConverterNodeFactory.create(0, 1, true, false, null, null));
        }
    }

    private Object convertOperand(VirtualFrame frame, Object vector, int operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(frame, vector, operand);
    }

    private Object convertOperand(VirtualFrame frame, Object vector, String operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(frame, vector, operand);
    }

    private Object setMultiDimData(VirtualFrame frame, RAbstractContainer value, RAbstractVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase,
                    int accSrcDimensions, int accDstDimensions, NACheck posNACheck, NACheck elementNACheck) {
        if (setMultiDimData == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setMultiDimData = insert(SetMultiDimDataNodeFactory.create(posNACheck, elementNACheck, this.isSubset, null, null, null, null, null, null, null, null));
        }
        return setMultiDimData.executeMultiDimDataSet(frame, value, vector, positions, currentDimLevel, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
    }

    @CreateCast({"newValue"})
    public RNode createCastValue(RNode child) {
        return CastToContainerNodeFactory.create(child, false, false, false, true);
    }

    @Specialization(guards = "emptyValue")
    RAbstractVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (isSubset) {
            int replacementLength = getReplacementLength(frame, positions, value, false);
            if (replacementLength == 0) {
                return vector;
            }
        }
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
    }

    @Specialization
    RNull accessFunction(VirtualFrame frame, Object v, Object value, int recLevel, Object position, RFunction vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.OBJECT_NOT_SUBSETTABLE, "closure");
    }

    @Specialization
    RAbstractVector update(VirtualFrame frame, Object v, RNull value, int recLevel, Object[] positions, RList vector) {
        if (isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBSCRIPT_TYPES, "NULL", "list");
        }
    }

    @Specialization(guards = "isPosZero")
    RAbstractVector updateNAOrZero(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RList vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            return vector;
        }
    }

    @Specialization
    RAbstractVector update(VirtualFrame frame, Object v, RNull value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
    }

    @Specialization(guards = {"emptyValue", "isPosZero"})
    RAbstractVector updatePosZero(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = {"emptyValue", "!isPosZero", "!isPosNA", "!isVectorList"})
    RAbstractVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
    }

    @Specialization(guards = "!isVectorLongerThanOne")
    RAbstractVector updateVectorLongerThanOne(VirtualFrame frame, Object v, RNull value, int recLevel, RNull position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @Specialization(guards = "isVectorLongerThanOne")
    RAbstractVector update(VirtualFrame frame, Object v, RNull value, int recLevel, RNull position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization
    RAbstractVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RNull position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"isPosNA", "isValueLengthOne", "isVectorLongerThanOne"})
    RAbstractVector updateNAValueLengthOneLongVector(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            return vector;
        }
    }

    @Specialization(guards = {"isPosNA", "isValueLengthOne", "!isVectorLongerThanOne"})
    RAbstractVector updateNAValueLengthOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            return vector;
        }
    }

    @Specialization(guards = {"isPosNA", "!isValueLengthOne"})
    RAbstractVector updateNA(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NA_SUBSCRIPTED);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
    }

    @Specialization(guards = {"isPosZero", "isValueLengthOne"})
    RAbstractVector updateZeroValueLengthOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            return vector;
        }
    }

    @Specialization(guards = {"isPosZero", "!isValueLengthOne"})
    RAbstractVector updateZero(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            return vector;
        }
    }

// @Specialization(guards = "isPosZero")
// RAbstractVector updateZero(VirtualFrame frame, Object v, RNull value, int recLevel, int position,
// RAbstractVector vector) {
// if (!isSubset) {
// throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
// } else {
// return vector;
// }
// }

    private int getSrcArrayBase(VirtualFrame frame, int pos, int accSrcDimensions) {
        if (posNACheck.check(pos)) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NA_SUBSCRIPTED);
        } else {
            return accSrcDimensions * (pos - 1);
        }
    }

    private int getReplacementLength(VirtualFrame frame, Object[] positions, RAbstractContainer value, boolean isList) {
        int valueLength = value.getLength();
        int length = 1;
        boolean seenNA = false;
        for (int i = 0; i < positions.length; i++) {
            RIntVector p = (RIntVector) positions[i];
            int len = p.getLength();
            posNACheck.enable(p);
            boolean allZeros = true;
            for (int j = 0; j < len; j++) {
                int pos = p.getDataAt(j);
                if (pos != 0) {
                    allZeros = false;
                    if (posNACheck.check(pos)) {
                        if (len == 1) {
                            seenNAMultiDim(frame, true, value, isList, isSubset, getEncapsulatingSourceSection());
                        } else {
                            seenNA = true;
                        }
                    }
                }
            }
            if (allZeros) {
                length = 0;
            } else {
                length *= p.getLength();
            }
        }
        if (valueLength != 0 && length != 0 && length % valueLength != 0) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else if (seenNA) {
            seenNAMultiDim(frame, true, value, isList, isSubset, getEncapsulatingSourceSection());
        }
        return length;
    }

    private int getHighestPos(RIntVector positions) {
        int highestPos = 0;
        posNACheck.enable(positions);
        int numNAs = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (posNACheck.check(pos)) {
                // ignore
                numNAs++;
                continue;
            } else if (pos < 0) {
                if (-pos > highestPos) {
                    highestPos = -pos;
                }
            } else if (pos > highestPos) {
                highestPos = pos;
            }
        }
        if (numNAs == positions.getLength()) {
            return numNAs;
        } else {
            return highestPos;
        }
    }

    private static RStringVector getNamesVector(RVector resultVector) {
        if (resultVector.getNames() == RNull.instance) {
            String[] namesData = new String[resultVector.getLength()];
            Arrays.fill(namesData, RRuntime.NAMES_ATTR_EMPTY_VALUE);
            RStringVector names = RDataFactory.createStringVector(namesData, RDataFactory.COMPLETE_VECTOR);
            resultVector.setNames(names);
            return names;
        } else {
            return (RStringVector) resultVector.getNames();
        }
    }

    private void updateNames(RVector resultVector, RIntVector positions) {
        if (positions.getNames() != RNull.instance) {
            RStringVector names = getNamesVector(resultVector);
            RStringVector newNames = (RStringVector) positions.getNames();
            namesNACheck.enable(newNames);
            for (int i = 0; i < positions.getLength(); i++) {
                int p = positions.getDataAt(i);
                names.updateDataAt(p - 1, newNames.getDataAt(i), namesNACheck);
            }
        }
    }

    // null

    @Specialization
    RNull updateWrongDimensions(Object v, RNull value, int recLevel, Object[] positions, RNull vector) {
        return vector;
    }

    @Specialization(guards = {"!wrongDimensionsMatrix", "!wrongDimensions"})
    RNull updateWrongDimensions(Object v, RAbstractVector value, int recLevel, Object[] positions, RNull vector) {
        return vector;
    }

    @Specialization(guards = "emptyValue")
    RNull updatePosZero(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RNull vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = "emptyValue")
    RNull updatePosZero(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RNull vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = "!emptyValue")
    RIntVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        int[] data = new int[highestPos];
        Arrays.fill(data, RRuntime.INT_NA);
        return updateSingleDimVector(frame, value, 0, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RIntVector update(Object v, RAbstractIntVector value, int recLevel, int position, RNull vector) {
        if (position > 1) {
            int[] data = new int[position];
            Arrays.fill(data, RRuntime.INT_NA);
            return updateSingleDim(value, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createIntVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue")
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos];
        Arrays.fill(data, RRuntime.DOUBLE_NA);
        return updateSingleDimVector(frame, value, 0, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RDoubleVector update(Object v, RAbstractDoubleVector value, int recLevel, int position, RNull vector) {
        if (position > 1) {
            double[] data = new double[position];
            Arrays.fill(data, RRuntime.DOUBLE_NA);
            return updateSingleDim(value, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createDoubleVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue")
    RLogicalVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        byte[] data = new byte[highestPos];
        Arrays.fill(data, RRuntime.LOGICAL_NA);
        return updateSingleDimVector(frame, value, 0, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RLogicalVector update(Object v, RAbstractLogicalVector value, int recLevel, int position, RNull vector) {
        if (position > 1) {
            byte[] data = new byte[position];
            Arrays.fill(data, RRuntime.LOGICAL_NA);
            return updateSingleDim(value, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createLogicalVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue")
    RStringVector update(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        String[] data = new String[highestPos];
        Arrays.fill(data, RRuntime.STRING_NA);
        return updateSingleDimVector(frame, value, 0, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RStringVector update(Object v, RAbstractStringVector value, int recLevel, int position, RNull vector) {
        if (position > 1) {
            String[] data = new String[position];
            Arrays.fill(data, RRuntime.STRING_NA);
            return updateSingleDim(value, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createStringVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue")
    RComplexVector update(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos << 1];
        int ind = 0;
        for (int i = 0; i < highestPos; i++) {
            data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
            data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
        }
        return updateSingleDimVector(frame, value, 0, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, int position, RNull vector) {
        if (position > 1) {
            double[] data = new double[position << 1];
            int ind = 0;
            for (int i = 0; i < position; i++) {
                data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
                data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
            }
            return updateSingleDim(frame, value, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(frame, value, RDataFactory.createComplexVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue")
    RRawVector update(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, RIntVector positions, RNull vector) {
        return updateSingleDimVector(frame, value, 0, RDataFactory.createRawVector(getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!emptyValue", "!isPosNA", "!isPosZero"})
    RRawVector update(Object v, RAbstractRawVector value, int recLevel, int position, RNull vector) {
        return updateSingleDim(value, RDataFactory.createRawVector(position), position);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "!isVectorList"})
    RList updateNegativeNull(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "!outOfBoundsNegative"})
    RList updateNegativeNull(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "oneElemVector"})
    RList updateNegativeOutOfBoundsOneElemNull(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "!oneElemVector"})
    RList updateNegativeOutOfBoundsNull(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "!outOfBoundsNegative"})
    RList updateNegative(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "oneElemVector"})
    RList updateNegativeOneElem(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @Specialization(guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "!oneElemVector"})
    RList updateOutOfBoundsNegative(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    // list

    private RList updateVector(VirtualFrame frame, RAbstractContainer value, RList vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, true);
        RList resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RList) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, true, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RList getResultVector(RList vector, int highestPos, boolean resetDims) {
        RList resultVector = vector;
        if (resultVector.isShared()) {
            resultVector = (RList) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            int orgLength = resultVector.getLength();
            resultVector.resizeWithNames(highestPos);
            for (int i = orgLength; i < highestPos; i++) {
                resultVector.updateDataAt(i, RNull.instance, null);
            }
        } else if (resetDims) {
            resultVector.setDimensions(null);
            resultVector.setDimNames(null);
        }
        return resultVector;
    }

    private int getPositionInRecursion(VirtualFrame frame, RList vector, int position, int recLevel, boolean lastPos) {
        if (RRuntime.isNA(position)) {
            CompilerDirectives.transferToInterpreter();
            if (lastPos && recLevel > 0) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else if (recLevel == 0) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NO_SUCH_INDEX, recLevel + 1);
            }
        } else if (!lastPos && position > vector.getLength()) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NO_SUCH_INDEX, recLevel + 1);
        } else if (position < 0) {
            CompilerDirectives.transferToInterpreter();
            return AccessArrayNode.getPositionFromNegative(frame, vector, position, getEncapsulatingSourceSection());
        } else if (position == 0) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        return position;
    }

    private static RList updateSingleDim(RAbstractContainer value, RList resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAtAsObject(0), null);
        return resultVector;
    }

    // this is similar to what happens on "regular" (non-vector) assignment - the state has to
    // change to avoid erroneous sharing
    private static RShareable adjustRhsStateOnAssignment(RAbstractContainer value) {
        RShareable val = value.materializeToShareable();
        if (val.isShared()) {
            val = val.copy();
        } else if (!val.isTemporary()) {
            val.makeShared();
        } else if (val.isTemporary()) {
            val.markNonTemporary();
        }
        return val;
    }

    private RList updateSingleDimRec(VirtualFrame frame, RAbstractContainer value, RList resultVector, RIntVector p, int recLevel) {
        int position = getPositionInRecursion(frame, resultVector, p.getDataAt(0), recLevel, true);
        resultVector.updateDataAt(position - 1, adjustRhsStateOnAssignment(value), null);
        updateNames(resultVector, p);
        return resultVector;
    }

    private RList updateSingleDimVector(VirtualFrame frame, RAbstractContainer value, int orgVectorLength, RList resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RNull.instance, null);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAtAsObject(i % value.getLength()), null);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    private Object updateListRecursive(VirtualFrame frame, Object v, Object value, RList vector, int recLevel, RStringVector p) {
        int position = AccessArrayNode.getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, getEncapsulatingSourceSection());
        if (p.getLength() == 2 && RRuntime.isNA(p.getDataAt(1))) {
            // catch it here, otherwise it will get caught at lower level of recursion resulting in
            // a different message
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        Object el;
        RList resultList = vector;
        if (recLevel == 0) {
            // TODO: does it matter to make it smarter (mark nested lists as shared during copy?)
            resultList = (RList) vector.deepCopy();
        }
        if (p.getLength() == 2) {
            Object finalVector = coerceVector(frame, resultList.getDataAt(position - 1), value, p);
            Object lastPosition = castPosition(frame, finalVector, convertOperand(frame, finalVector, p.getDataAt(1)));
            el = updateRecursive(frame, v, value, finalVector, lastPosition, recLevel + 1);
        } else {
            RStringVector newP = AccessArrayNode.popHead(p, posNACheck);
            el = updateRecursive(frame, v, value, resultList.getDataAt(position - 1), newP, recLevel + 1);
        }

        resultList.updateDataAt(position - 1, el, null);
        return resultList;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RList update(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, Object[] positions, RList vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization
    Object updateString(VirtualFrame frame, Object v, RNull value, int recLevel, RStringVector positions, RList vector) {
        return updateListRecursive(frame, v, value, vector, recLevel, positions);
    }

    @Specialization
    Object updateString(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RStringVector positions, RList vector) {
        return updateListRecursive(frame, v, value, vector, recLevel, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RList update(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector positions, RList vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateOne(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector positions, RList vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RList updateNames(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector positions, RList vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList updateTooManyValuesSubset(Object v, RAbstractContainer value, int recLevel, int position, RList vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(guards = {"isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList update(Object v, RAbstractContainer value, int recLevel, int position, RList vector) {
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(guards = {"!isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList updateTooManyValuesSubscript(Object v, RAbstractContainer value, int recLevel, int position, RList vector) {
        RList resultVector = getResultVector(vector, position, false);
        resultVector.updateDataAt(position - 1, adjustRhsStateOnAssignment(value), null);
        return resultVector;
    }

    @Specialization(guards = "isPosNA")
    RList updateListNullValue(Object v, RNull value, int recLevel, int position, RList vector) {
        return vector;
    }

    @Specialization(guards = {"!isPosZero", "emptyList", "!isPosNA", "!isPositionNegative"})
    RList updateEmptyList(Object v, RNull value, int recLevel, int position, RList vector) {
        return vector;
    }

    private RList removeElement(RList vector, int position, boolean inRecursion, boolean resetDims) {
        if (position > vector.getLength()) {
            if (inRecursion || !isSubset) {
                // simply return the vector unchanged
                return vector;
            } else {
                // this is equivalent to extending the vector to appropriate length and then
                // removing the last element
                return getResultVector(vector, position - 1, resetDims);
            }
        } else {
            Object[] data = new Object[vector.getLength() - 1];
            RStringVector orgNames = null;
            String[] namesData = null;
            if (vector.getNames() != RNull.instance) {
                namesData = new String[vector.getLength() - 1];
                orgNames = (RStringVector) vector.getNames();
            }

            int ind = 0;
            for (int i = 0; i < vector.getLength(); i++) {
                if (i != (position - 1)) {
                    data[ind] = vector.getDataAt(i);
                    if (orgNames != null) {
                        namesData[ind] = orgNames.getDataAt(i);
                    }
                    ind++;
                }
            }

            RList result;
            if (orgNames == null) {
                result = RDataFactory.createList(data);
            } else {
                result = RDataFactory.createList(data, RDataFactory.createStringVector(namesData, vector.isComplete()));
            }
            result.copyRegAttributesFrom(vector);
            return result;
        }
    }

    @Specialization(guards = {"!isPosZero", "!emptyList", "!isPosNA", "!isPositionNegative"})
    RList update(Object v, RNull value, int recLevel, int position, RList vector) {
        return removeElement(vector, position, false, isSubset);
    }

    private static final Object DELETE_MARKER = new Object();

    @Specialization(guards = {"isSubset", "noPosition"})
    RList updateEmptyPos(Object v, RNull value, int recLevel, RIntVector positions, RList vector) {
        return vector;
    }

    @Specialization(guards = {"isSubset", "!noPosition"})
    RList update(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RList vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }

        RList list = vector;
        if (list.isShared()) {
            list = (RList) vector.copy();
            list.markNonTemporary();
        }
        int highestPos = getHighestPos(positions);
        if (list.getLength() < highestPos) {
            // to mark duplicate deleted elements with positions > vector length
            list = list.copyResized(highestPos, false);
        }
        int posDeleted = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (RRuntime.isNA(pos) || pos < 0) {
                continue;
            }
            if (list.getDataAt(pos - 1) != DELETE_MARKER) {
                list.updateDataAt(pos - 1, DELETE_MARKER, null);
                // count each position only once
                posDeleted++;
            }
        }
        int resultVectorLength = highestPos > list.getLength() ? highestPos - posDeleted : list.getLength() - posDeleted;
        Object[] data = new Object[resultVectorLength];
        RStringVector orgNames = null;
        String[] namesData = null;
        if (vector.getNames() != RNull.instance) {
            namesData = new String[resultVectorLength];
            orgNames = (RStringVector) vector.getNames();
        }

        int ind = 0;
        for (int i = 0; i < vector.getLength(); i++) {
            Object el = list.getDataAt(i);
            if (el != DELETE_MARKER) {
                data[ind] = el;
                if (orgNames != null) {
                    namesData[ind] = orgNames.getDataAt(i);
                }
                ind++;
            }
        }
        for (; ind < data.length; ind++) {
            data[ind] = RNull.instance;
            if (orgNames != null) {
                namesData[ind] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
            }
        }
        RList result;
        if (orgNames == null) {
            result = RDataFactory.createList(data);
        } else {
            result = RDataFactory.createList(data, RDataFactory.createStringVector(namesData, orgNames.isComplete()));
        }
        result.copyRegAttributesFrom(vector);
        return result;
    }

    private Object updateListRecursive(VirtualFrame frame, Object v, Object value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, false);
        if (p.getLength() == 2 && RRuntime.isNA(p.getDataAt(1))) {
            // catch it here, otherwise it will get caught at lower level of recursion resulting in
            // a different message
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        }
        RList resultList = vector;
        if (recLevel == 0) {
            // TODO: does it matter to make it smarter (mark nested lists as shared during copy?)
            resultList = (RList) vector.deepCopy();
        }
        Object el;
        if (p.getLength() == 2) {
            Object finalVector = coerceVector(frame, resultList.getDataAt(position - 1), value, p);
            Object lastPosition = castPosition(frame, finalVector, convertOperand(frame, finalVector, p.getDataAt(1)));
            el = updateRecursive(frame, v, value, finalVector, lastPosition, recLevel + 1);
        } else {
            RIntVector newP = AccessArrayNode.popHead(p, posNACheck);
            el = updateRecursive(frame, v, value, resultList.getDataAt(position - 1), newP, recLevel + 1);
        }

        resultList.updateDataAt(position - 1, el, null);
        return resultList;
    }

    @Specialization(guards = {"!isSubset", "multiPos"})
    Object access(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector p, RList vector) {
        return updateListRecursive(frame, v, value, vector, recLevel, p);
    }

    @Specialization(guards = {"!isSubset", "multiPos"})
    Object access(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector p, RList vector) {
        return updateListRecursive(frame, v, value, vector, recLevel, p);
    }

    @Specialization(guards = {"!isSubset", "inRecursion", "multiPos"})
    Object accessRecFailed(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector p, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.RECURSIVE_INDEXING_FAILED, recLevel + 1);
    }

    @Specialization(guards = {"!isSubset", "!multiPos"})
    Object accessSubscriptListValue(VirtualFrame frame, Object v, RList value, int recLevel, RIntVector p, RList vector) {
        int position = getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, true);
        return updateSingleDimRec(frame, value, getResultVector(vector, position, false), p, recLevel);
    }

    @Specialization(guards = {"!isSubset", "inRecursion", "!multiPos"})
    Object accessSubscriptNullValueInRecursion(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector p, RList vector) {
        int position = getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, true);
        return removeElement(vector, position, true, false);
    }

    @Specialization(guards = {"!isSubset", "!inRecursion", "!multiPos"})
    Object accessSubscriptNullValue(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector p, RList vector) {
        int position = getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, true);
        return removeElement(vector, position, false, false);
    }

    @Specialization(guards = {"!isSubset", "!multiPos"})
    Object accessSubscript(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, RIntVector p, RList vector) {
        int position = getPositionInRecursion(frame, vector, p.getDataAt(0), recLevel, true);
        return updateSingleDimRec(frame, value, getResultVector(vector, position, false), p, recLevel);
    }

    @Specialization(guards = {"!isValueLengthOne", "!emptyValue", "!isSubset", "!isPosNA", "!isPosZero"})
    RAbstractVector updateTooManyValues(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, int position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
    }

    // null value (with vectors)

    @Specialization(guards = {"isPosZero", "!isVectorList"})
    RAbstractVector updatePosZero(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = {"!isPosZero", "!isPosNA", "!isVectorList"})
    RAbstractVector update(VirtualFrame frame, Object v, RNull value, int recLevel, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        }
    }

    @Specialization(guards = {"isSubset", "!isVectorList", "noPosition"})
    RAbstractVector updateNullSubsetNoPos(Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        return vector;
    }

    @Specialization(guards = {"isSubset", "!isVectorList", "!noPosition"})
    RAbstractVector updateNullSubset(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "noPosition"})
    RAbstractVector updateNullNoPos(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "onePosition"})
    RAbstractVector updateNullOnePos(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "twoPositions", "firstPosZero"})
    RAbstractVector updateNullTwoElemsZero(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "twoPositions", "!firstPosZero"})
    RAbstractVector updateNullTwoElems(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "multiPos"})
    RAbstractVector updateNull(VirtualFrame frame, Object v, RNull value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    // int vector

    private RIntVector updateVector(VirtualFrame frame, RAbstractIntVector value, RAbstractIntVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RIntVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            resultVector = (RIntVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RIntVector getResultVector(RAbstractIntVector vector, int highestPos) {
        RIntVector resultVector = vector.materialize();
        if (resultVector.isShared()) {
            resultVector = (RIntVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RIntVector updateSingleDim(RAbstractIntVector value, RIntVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private boolean seenNA(VirtualFrame frame, int p, RAbstractContainer value) {
        if (posNACheck.check(p) || p < 0) {
            if (value.getLength() == 1) {
                return true;
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NA_SUBSCRIPTED);
            }
        } else {
            return false;
        }
    }

    protected static boolean seenNAMultiDim(VirtualFrame frame, boolean isPosNA, RAbstractContainer value, boolean isList, boolean isSubset, SourceSection sourceSection) {
        if (isPosNA) {
            if (value.getLength() == 1) {
                if (!isSubset) {
                    throw RError.error(frame, sourceSection, RError.Message.SUBSCRIPT_BOUNDS_SUB);
                } else {
                    return true;
                }
            } else {
                CompilerDirectives.transferToInterpreter();
                if (!isSubset) {
                    if (isList) {
                        throw RError.error(frame, sourceSection, RError.Message.SUBSCRIPT_BOUNDS_SUB);
                    } else {
                        throw RError.error(frame, sourceSection, RError.Message.MORE_SUPPLIED_REPLACE);
                    }
                } else {
                    throw RError.error(frame, sourceSection, RError.Message.NA_SUBSCRIPTED);
                }
            }
        } else {
            return false;
        }

    }

    private RIntVector updateSingleDimVector(VirtualFrame frame, RAbstractIntVector value, int orgVectorLength, RIntVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.INT_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "!posNames", "!twoPositions"})
    Object update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "!posNames", "twoPositions", "firstPosZero"})
    RList updateTwoElemsZero(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
    }

    @Specialization(guards = {"!isSubset", "!isVectorList", "!posNames", "twoPositions", "!firstPosZero"})
    RList updateTwoElems(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RIntVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, Object[] positions, RAbstractIntVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, Object[] positions, RAbstractIntVector vector) {
        return updateVector(frame, (RIntVector) castInteger(frame, value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractIntVector updateSubset(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractIntVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractIntVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RIntVector updateTooManyValuesSubset(Object v, RAbstractIntVector value, int recLevel, int position, RAbstractIntVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RIntVector update(Object v, RAbstractIntVector value, int recLevel, int position, RAbstractIntVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractIntVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, (RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractIntVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, (RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(frame, (RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RIntVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RAbstractIntVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RIntVector) castInteger(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RAbstractIntVector vector) {
        return updateSingleDim((RIntVector) castInteger(frame, value), getResultVector(vector, position), position);
    }

    // double vector

    private RDoubleVector updateVector(VirtualFrame frame, RAbstractDoubleVector value, RAbstractDoubleVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RDoubleVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            resultVector = (RDoubleVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RDoubleVector getResultVector(RAbstractDoubleVector vector, int highestPos) {
        RDoubleVector resultVector = vector.materialize();
        if (resultVector.isShared()) {
            resultVector = (RDoubleVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RDoubleVector updateSingleDim(RAbstractDoubleVector value, RDoubleVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RDoubleVector updateSingleDimVector(VirtualFrame frame, RAbstractDoubleVector value, int orgVectorLength, RDoubleVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.DOUBLE_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (value.getLength() == 0) {
            Utils.nyi();
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(frame, (RDoubleVector) castDouble(frame, value), vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(frame, (RDoubleVector) castDouble(frame, value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(Object v, RAbstractDoubleVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(Object v, RAbstractDoubleVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(frame, (RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RAbstractDoubleVector vector) {
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    // logical vector

    private RLogicalVector updateVector(VirtualFrame frame, RAbstractLogicalVector value, RLogicalVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RLogicalVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RLogicalVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RLogicalVector getResultVector(RLogicalVector vector, int highestPos) {
        RLogicalVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RLogicalVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RLogicalVector updateSingleDim(RAbstractLogicalVector value, RLogicalVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RLogicalVector updateSingleDimVector(VirtualFrame frame, RAbstractLogicalVector value, int orgVectorLength, RLogicalVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.LOGICAL_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RLogicalVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, Object[] positions, RLogicalVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractLogicalVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RLogicalVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RLogicalVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractLogicalVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RLogicalVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractLogicalVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RLogicalVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RLogicalVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, int recLevel, int position, RLogicalVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RLogicalVector update(Object v, RAbstractLogicalVector value, int recLevel, int position, RLogicalVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    // string vector

    private RStringVector updateVector(VirtualFrame frame, RAbstractStringVector value, RStringVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RStringVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RStringVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RStringVector getResultVector(RStringVector vector, int highestPos) {
        RStringVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RStringVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RStringVector updateSingleDim(RAbstractStringVector value, RStringVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RStringVector updateSingleDimVector(VirtualFrame frame, RAbstractStringVector value, int orgVectorLength, RStringVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.STRING_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RStringVector update(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, Object[] positions, RStringVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RStringVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, Object[] positions, RStringVector vector) {
        return updateVector(frame, (RStringVector) castString(frame, value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractStringVector updateSubset(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractStringVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractStringVector update(VirtualFrame frame, Object v, RAbstractStringVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RStringVector updateTooManyValuesSubset(Object v, RAbstractStringVector value, int recLevel, int position, RStringVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RStringVector update(Object v, RAbstractStringVector value, int recLevel, int position, RStringVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractStringVector updateSubset(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, (RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractStringVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, (RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractStringVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(frame, (RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RStringVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RStringVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RStringVector) castString(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RStringVector update(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, int position, RStringVector vector) {
        return updateSingleDim((RStringVector) castString(frame, value), getResultVector(vector, position), position);
    }

    // complex vector

    private RComplexVector updateVector(VirtualFrame frame, RAbstractComplexVector value, RComplexVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RComplexVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RComplexVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RComplexVector getResultVector(RComplexVector vector, int highestPos) {
        RComplexVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RComplexVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RComplexVector updateSingleDim(VirtualFrame frame, RAbstractComplexVector value, RComplexVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RComplexVector updateSingleDimVector(VirtualFrame frame, RAbstractComplexVector value, int orgVectorLength, RComplexVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.createComplexNA(), elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, Object[] positions, RComplexVector vector) {
        return updateVector(frame, (RComplexVector) castComplex(frame, value), vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, Object[] positions, RComplexVector vector) {
        return updateVector(frame, (RComplexVector) castComplex(frame, value), vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, Object[] positions, RComplexVector vector) {
        return updateVector(frame, (RComplexVector) castComplex(frame, value), vector, positions);
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, Object[] positions, RComplexVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, int position, RComplexVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, int recLevel, int position, RComplexVector vector) {
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, int position, RComplexVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, int recLevel, int position, RComplexVector vector) {
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, (RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RComplexVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, int recLevel, int position, RComplexVector vector) {
        return updateSingleDim(frame, (RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, int position, RComplexVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(frame, value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractComplexVector value, int recLevel, int position, RComplexVector vector) {
        return updateSingleDim(frame, value, getResultVector(vector, position), position);
    }

    // raw vector

    private RRawVector updateVector(VirtualFrame frame, RAbstractRawVector value, RRawVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(frame, positions, value, false);
        RRawVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RRawVector) vector.copy();
            resultVector.markNonTemporary();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(frame, pos, accSrcDimensions);
            setMultiDimData(frame, value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private static RRawVector getResultVector(RRawVector vector, int highestPos) {
        RRawVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RRawVector) vector.copy();
            resultVector.markNonTemporary();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private static RRawVector updateSingleDim(RAbstractRawVector value, RRawVector resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAt(0));
        return resultVector;
    }

    private RRawVector updateSingleDimVector(VirtualFrame frame, RAbstractRawVector value, int orgVectorLength, RRawVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(frame, p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RDataFactory.createRaw((byte) 0));
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()));
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RRawVector update(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, Object[] positions, RRawVector vector) {
        return updateVector(frame, value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractRawVector updateSubset(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, RIntVector positions, RRawVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, RIntVector positions, RRawVector vector) {
        return updateRecursive(frame, v, value, vector, positions.getDataAt(0), recLevel);
    }

    @Specialization(guards = {"isSubset", "posNames"})
    RAbstractRawVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, RIntVector positions, RRawVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isSubset", "posNames"})
    RAbstractRawVector update(VirtualFrame frame, Object v, RAbstractRawVector value, int recLevel, RIntVector positions, RRawVector vector) {
        return updateSingleDimVector(frame, value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RRawVector updateTooManyValuesSubset(Object v, RAbstractRawVector value, int recLevel, int position, RRawVector vector) {
        RError.warning(RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RRawVector update(Object v, RAbstractRawVector value, int recLevel, int position, RRawVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"noPosition", "emptyValue"})
    Object accessListEmptyPosEmptyValueList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RList vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"noPosition", "emptyValue", "!isVectorList"})
    Object accessListEmptyPosEmptyValue(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"noPosition", "valueLengthOne"})
    Object accessListEmptyPosValueLengthOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"noPosition", "valueLongerThanOne"})
    Object accessListEmptyPosValueLongerThanOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = "noPosition")
    Object accessListEmptyPosValueNullList(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RList vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"noPosition", "!isVectorList"})
    Object accessListEmptyPosValueNull(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"onePosition", "emptyValue"})
    Object accessListOnePosEmptyValueList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"onePosition", "emptyValue", "!isVectorList"})
    Object accessListOnePosEmptyValue(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"onePosition", "valueLengthOne"})
    Object accessListOnePosValueLengthOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"onePosition", "valueLongerThanOne"})
    Object accessListOnePosValueLongerThanTwo(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = "onePosition")
    Object accessListOnePosValueNullList(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"onePosition", "!isVectorList"})
    Object accessListOnePosValueNull(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = "twoPositions")
    Object accessListTwoPos(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = "twoPositions")
    Object accessListTwoPosValueNull(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"moreThanTwoPos", "emptyValue"})
    Object accessListMultiPosEmptyValueList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"moreThanTwoPos", "emptyValue", "!isVectorList"})
    Object accessListMultiPosEmptyValue(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"moreThanTwoPos", "valueLengthOne"})
    Object accessListMultiPosValueLengthOneList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"moreThanTwoPos", "valueLengthOne", "!isVectorList"})
    Object accessListMultiPosValueLengthOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"moreThanTwoPos", "valueLongerThanOne"})
    Object accessListMultiPos(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = "moreThanTwoPos")
    Object accessListMultiPosValueNullList(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @Specialization(guards = {"moreThanTwoPos", "!isVectorList"})
    Object accessListMultiPosValueNull(VirtualFrame frame, Object v, RNull value, int recLevel, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
        }
    }

    @Specialization(guards = {"emptyValue", "!isVectorList"})
    Object accessComplexEmptyValue(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RComplex position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }
    }

    @Specialization(guards = {"valueLongerThanOne", "!isVectorList"})
    Object accessComplexValueLongerThanOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RComplex position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }
    }

    @Specialization(guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
    Object accessComplex(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RComplex position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
    }

    @Specialization
    Object accessComplexList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RComplex position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
    }

    @Specialization(guards = "!isVectorList")
    Object accessComplex(VirtualFrame frame, Object v, RNull value, int recLevel, RComplex position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }
    }

    @Specialization
    Object accessComplexList(VirtualFrame frame, Object v, RNull value, int recLevel, RComplex position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
    }

    @Specialization(guards = {"emptyValue", "!isVectorList"})
    Object accessRawEmptyValue(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RRaw position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }
    }

    @Specialization(guards = {"valueLongerThanOne", "!isVectorList"})
    Object accessRawValueLongerThanOne(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RRaw position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }
    }

    @Specialization(guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
    Object accessRaw(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RRaw position, RAbstractVector vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
    }

    @Specialization
    Object accessRawList(VirtualFrame frame, Object v, RAbstractVector value, int recLevel, RRaw position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
    }

    @Specialization(guards = "!isVectorList")
    Object accessRaw(VirtualFrame frame, Object v, RNull value, int recLevel, RRaw position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }
    }

    @Specialization
    Object accessRawList(VirtualFrame frame, Object v, RNull value, int recLevel, RRaw position, RList vector) {
        throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
    }

    protected boolean firstPosZero(Object v, RAbstractContainer value, int recLevel, RIntVector positions) {
        return positions.getDataAt(0) == 0;
    }

    protected boolean firstPosZero(Object v, RNull value, int recLevel, RIntVector positions) {
        return positions.getDataAt(0) == 0;
    }

    protected boolean outOfBoundsNegative(Object v, RNull value, int recLevel, int position, RAbstractVector vector) {
        return -position > vector.getLength();
    }

    protected boolean outOfBoundsNegative(Object v, RAbstractContainer value, int recLevel, int position, RAbstractVector vector) {
        return -position > vector.getLength();
    }

    protected boolean oneElemVector(Object v, RNull value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getLength() == 1;
    }

    protected boolean oneElemVector(Object v, RAbstractContainer value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getLength() == 1;
    }

    protected boolean posNames(Object v, RAbstractContainer value, int recLevel, RIntVector positions) {
        return positions.getNames() != RNull.instance;
    }

    protected boolean isPositionNegative(Object v, RAbstractContainer value, int recLevel, int position) {
        return position < 0;
    }

    protected boolean isPositionNegative(Object v, RNull value, int recLevel, int position) {
        return position < 0;
    }

    protected boolean isVectorList(Object v, RNull value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getElementClass() == Object.class;
    }

    protected boolean isVectorList(Object v, RAbstractContainer value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getElementClass() == Object.class;
    }

    protected boolean isVectorLongerThanOne(Object v, RAbstractContainer value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getLength() > 1;
    }

    protected boolean isVectorLongerThanOne(Object v, RNull value, int recLevel, Object positions, RAbstractVector vector) {
        return vector.getLength() > 1;
    }

    protected boolean emptyValue(Object v, RAbstractContainer value) {
        return value.getLength() == 0;
    }

    protected boolean valueLengthOne(Object v, RAbstractContainer value) {
        return value.getLength() == 1;
    }

    protected boolean valueLongerThanOne(Object v, RAbstractContainer value) {
        return value.getLength() > 1;
    }

    protected boolean wrongDimensionsMatrix(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (positions.length == 2 && (vector.getDimensions() == null || vector.getDimensions().length != positions.length)) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS_MATRIX);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensionsMatrix(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, Object[] positions, RNull vector) {
        if (positions.length == 2) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS_MATRIX);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensionsMatrix(VirtualFrame frame, Object v, RNull value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (positions.length == 2 && (vector.getDimensions() == null || vector.getDimensions().length != positions.length)) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS_MATRIX);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensions(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (!((vector.getDimensions() == null && positions.length == 1) || vector.getDimensions().length == positions.length)) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensions(VirtualFrame frame, Object v, RAbstractContainer value, int recLevel, Object[] positions, RNull vector) {
        if (positions.length > 2) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensions(VirtualFrame frame, Object v, RNull value, int recLevel, Object[] positions, RAbstractVector vector) {
        if (!((vector.getDimensions() == null && positions.length == 1) || vector.getDimensions().length == positions.length)) {
            if (isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INCORRECT_SUBSCRIPTS);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean multiDim(Object v, RAbstractContainer value, int recLevel, Object[] positions, RAbstractVector vector) {
        return vector.getDimensions() != null && vector.getDimensions().length > 1;
    }

    protected boolean wrongLength(Object v, RAbstractContainer value, int recLevel, RIntVector positions, RAbstractVector vector) {
        int valLength = value.getLength();
        int posLength = positions.getLength();
        return valLength > posLength || (posLength % valLength != 0);
    }

    protected boolean isPosNA(Object v, RAbstractContainer value, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosNA(Object v, RNull value, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosZero(Object v, RAbstractContainer value, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isPosZero(Object v, RNull value, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isValueLengthOne(Object v, RAbstractContainer value) {
        return value.getLength() == 1;
    }

    protected boolean twoPositions(Object v, RAbstractContainer value, int recLevel, RAbstractVector position) {
        return position.getLength() == 2;
    }

    protected boolean twoPositions(Object v, RNull value, int recLevel, RAbstractVector position) {
        return position.getLength() == 2;
    }

    protected boolean onePosition(Object v, RAbstractContainer value, int recLevel, RAbstractVector position) {
        return position.getLength() == 1;
    }

    protected boolean onePosition(Object v, RNull value, int recLevel, RAbstractVector position) {
        return position.getLength() == 1;
    }

    protected boolean noPosition(Object v, RAbstractContainer value, int recLevel, RAbstractVector position) {
        return position.getLength() == 0;
    }

    protected boolean noPosition(Object v, RNull value, int recLevel, RAbstractVector position) {
        return position.getLength() == 0;
    }

    protected boolean isSubset() {
        return isSubset;
    }

    protected boolean inRecursion(Object v, RAbstractContainer value, int recLevel) {
        return recLevel > 0;
    }

    protected boolean inRecursion(Object v, RNull value, int recLevel) {
        return recLevel > 0;
    }

    protected boolean multiPos(Object v, RNull value, int recLevel, RIntVector positions) {
        return positions.getLength() > 1;
    }

    protected boolean multiPos(Object v, RAbstractContainer value, int recLevel, RIntVector positions) {
        return positions.getLength() > 1;
    }

    protected boolean moreThanTwoPos(Object v, RAbstractContainer value, int recLevel, RList positions) {
        return positions.getLength() > 2;
    }

    protected boolean moreThanTwoPos(Object v, RNull value, int recLevel, RList positions) {
        return positions.getLength() > 2;
    }

    protected boolean emptyList(Object v, RNull value, int recLevel, int positions, RList vector) {
        return vector.getLength() == 0;
    }

    @NodeChildren({@NodeChild(value = "val", type = RNode.class), @NodeChild(value = "vec", type = RNode.class), @NodeChild(value = "pos", type = RNode.class),
                    @NodeChild(value = "currDimLevel", type = RNode.class), @NodeChild(value = "srcArrayBase", type = RNode.class), @NodeChild(value = "dstArrayBase", type = RNode.class),
                    @NodeChild(value = "accSrcDimensions", type = RNode.class), @NodeChild(value = "accDstDimensions", type = RNode.class)})
    protected abstract static class SetMultiDimDataNode extends RNode {

        public abstract Object executeMultiDimDataSet(VirtualFrame frame, RAbstractContainer value, RAbstractVector vector, Object[] positions, int currentDimLevel, int srcArrayBase,
                        int dstArrayBase, int accSrcDimensions, int accDstDimensions);

        private final NACheck posNACheck;
        private final NACheck elementNACheck;
        private final boolean isSubset;

        @Child private SetMultiDimDataNode setMultiDimDataRecursive;

        private Object setMultiDimData(VirtualFrame frame, RAbstractVector value, RAbstractVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase,
                        int accSrcDimensions, int accDstDimensions, NACheck posCheck, NACheck elementCheck) {
            if (setMultiDimDataRecursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setMultiDimDataRecursive = insert(SetMultiDimDataNodeFactory.create(posCheck, elementCheck, this.isSubset, null, null, null, null, null, null, null, null));
            }
            return setMultiDimDataRecursive.executeMultiDimDataSet(frame, value, vector, positions, currentDimLevel, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }

        protected SetMultiDimDataNode(NACheck posNACheck, NACheck elementNACheck, boolean isSubset) {
            this.posNACheck = posNACheck;
            this.elementNACheck = elementNACheck;
            this.isSubset = isSubset;
        }

        protected SetMultiDimDataNode(SetMultiDimDataNode other) {
            this.posNACheck = other.posNACheck;
            this.elementNACheck = other.elementNACheck;
            this.isSubset = other.isSubset;
        }

        @Specialization
        RList setData(VirtualFrame frame, RAbstractVector value, RList vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, true, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAtAsObject(dstIndex % value.getLength()), null);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, true, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RIntVector setData(VirtualFrame frame, RAbstractIntVector value, RIntVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                        int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RDoubleVector setData(VirtualFrame frame, RAbstractDoubleVector value, RDoubleVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                        int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RLogicalVector setData(VirtualFrame frame, RAbstractLogicalVector value, RLogicalVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase,
                        int accSrcDimensions, int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RStringVector setData(VirtualFrame frame, RAbstractStringVector value, RStringVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                        int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RComplexVector setData(VirtualFrame frame, RAbstractComplexVector value, RComplexVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase,
                        int accSrcDimensions, int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        @Specialization
        RRawVector setData(VirtualFrame frame, RAbstractRawVector value, RRawVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                        int accDstDimensions) {
            int[] srcDimensions = vector.getDimensions();
            RIntVector p = (RIntVector) positions[currentDimLevel - 1];
            int srcDimSize = srcDimensions[currentDimLevel - 1];
            int newAccSrcDimensions = accSrcDimensions / srcDimSize;
            int newAccDstDimensions = accDstDimensions / p.getLength();
            posNACheck.enable(p);
            if (currentDimLevel == 1) {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int dstIndex = dstArrayBase + newAccDstDimensions * i;
                    int srcIndex = getSrcIndex(frame, srcArrayBase, pos, newAccSrcDimensions);
                    vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()));
                }
            } else {
                for (int i = 0; i < p.getLength(); i++) {
                    int pos = p.getDataAt(i);
                    if (seenNAMultiDim(frame, posNACheck.check(pos), value, false, isSubset, getEncapsulatingSourceSection())) {
                        continue;
                    }
                    int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                    int newSrcArrayBase = getNewArrayBase(frame, srcArrayBase, pos, newAccSrcDimensions);
                    setMultiDimData(frame, value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions, posNACheck, elementNACheck);
                }
            }
            return vector;
        }

        private int getNewArrayBase(VirtualFrame frame, int srcArrayBase, int pos, int newAccSrcDimensions) {
            int newSrcArrayBase;
            if (posNACheck.check(pos)) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NA_SUBSCRIPTED);
            } else {
                newSrcArrayBase = srcArrayBase + newAccSrcDimensions * (pos - 1);
            }
            return newSrcArrayBase;
        }

        private int getSrcIndex(VirtualFrame frame, int srcArrayBase, int pos, int newAccSrcDimensions) {
            if (posNACheck.check(pos)) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.NA_SUBSCRIPTED);
            } else {
                return srcArrayBase + newAccSrcDimensions * (pos - 1);
            }
        }

    }

    @NodeChildren({@NodeChild(value = "vector", type = RNode.class), @NodeChild(value = "newValue", type = RNode.class), @NodeChild(value = "operand", type = RNode.class)})
    public abstract static class MultiDimPosConverterValueNode extends RNode {

        public abstract RIntVector executeConvert(VirtualFrame frame, Object vector, Object value, Object p);

        private final boolean isSubset;

        protected MultiDimPosConverterValueNode(boolean isSubset) {
            this.isSubset = isSubset;
        }

        protected MultiDimPosConverterValueNode(MultiDimPosConverterValueNode other) {
            this.isSubset = other.isSubset;
        }

        @Specialization(guards = {"!singlePosNegative", "!multiPos"})
        public RAbstractIntVector doIntVector(RNull vector, RNull value, RAbstractIntVector positions) {
            return positions;
        }

        @Specialization(guards = {"!isPosVectorInt", "!multiPos"})
        public RAbstractVector doIntVector(RNull vector, RNull value, RAbstractVector positions) {
            return positions;
        }

        @Specialization(guards = {"!singlePosNegative", "multiPos"})
        public RAbstractIntVector doIntVectorMultiPos(VirtualFrame frame, RNull vector, RNull value, RAbstractIntVector positions) {
            if (isSubset) {
                return positions;
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            }
        }

        @Specialization(guards = {"!isPosVectorInt", "multiPos"})
        public RAbstractVector doIntVectorMultiPos(VirtualFrame frame, RNull vector, RNull value, RAbstractVector positions) {
            if (isSubset) {
                return positions;
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            }
        }

        @Specialization(guards = {"!emptyValue", "!singlePosNegative", "!multiPos"})
        public RAbstractIntVector doIntVector(RNull vector, RAbstractVector value, RAbstractIntVector positions) {
            return positions;
        }

        @Specialization(guards = {"!emptyValue", "!isPosVectorInt", "!multiPos"})
        public RAbstractVector doIntVector(RNull vector, RAbstractVector value, RAbstractVector positions) {
            return positions;
        }

        @Specialization(guards = {"!emptyValue", "!singlePosNegative", "multiPos"})
        public RAbstractIntVector doIntVectorMultiPos(VirtualFrame frame, RNull vector, RAbstractVector value, RAbstractIntVector positions) {
            if (isSubset) {
                return positions;
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            }
        }

        @Specialization(guards = {"!emptyValue", "!isPosVectorInt", "multiPos"})
        public RAbstractVector doIntVectorMultiPos(VirtualFrame frame, RNull vector, RAbstractVector value, RAbstractVector positions) {
            if (isSubset) {
                return positions;
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            }
        }

        @Specialization(guards = {"!emptyValue", "singlePosNegative"})
        public RAbstractIntVector doIntVectorNegative(VirtualFrame frame, RNull vector, RAbstractVector value, RAbstractIntVector positions) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
        }

        @Specialization(guards = "emptyValue")
        public RAbstractVector doIntVectorEmptyValue(VirtualFrame frame, RNull vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"emptyValue", "!isVectorList"})
        Object accessComplexEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RComplex position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
            }
        }

        @Specialization(guards = {"valueLongerThanOne", "!isVectorList"})
        Object accessComplexValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RComplex position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
            }
        }

        @Specialization(guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
        Object accessComplex(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RComplex position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }

        @Specialization
        Object accessComplexList(VirtualFrame frame, RList vector, RAbstractVector value, RComplex position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }

        @Specialization
        Object accessComplexList(VirtualFrame frame, RList vector, RNull value, RComplex position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        }

        @Specialization(guards = "!isVectorList")
        Object accessComplex(VirtualFrame frame, RAbstractVector vector, RNull value, RComplex position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
            }
        }

        @Specialization(guards = {"emptyValue", "!isVectorList"})
        Object accessRawEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RRaw position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
            }
        }

        @Specialization(guards = {"valueLongerThanOne", "!isVectorList"})
        Object accessRawValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RRaw position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
            }
        }

        @Specialization(guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
        Object accessRaw(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RRaw position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }

        @Specialization
        Object accessRawList(VirtualFrame frame, RList vector, RAbstractVector value, RRaw position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }

        @Specialization
        Object accessRawList(VirtualFrame frame, RList vector, RNull value, RRaw position) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        }

        @Specialization(guards = "!isVectorList")
        Object accessRaw(VirtualFrame frame, RAbstractVector vector, RNull value, RRaw position) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
            }
        }

        @Specialization(guards = {"noPosition", "emptyValue"})
        RAbstractVector accessListEmptyPosEmptyValueList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"noPosition", "emptyValue", "!isVectorList"})
        RAbstractVector accessListEmptyPosEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"noPosition", "valueLengthOne"})
        RAbstractVector accessListEmptyPosValueLengthOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"noPosition", "valueLongerThanOne"})
        RAbstractVector accessListEmptyPosValueLongerThanOneList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"noPosition", "valueLongerThanOne", "!isVectorList"})
        RAbstractVector accessListEmptyPosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = "noPosition")
        RAbstractVector accessListEmptyPosEmptyValueList(VirtualFrame frame, RList vector, RNull value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"noPosition", "!isVectorList"})
        RAbstractVector accessListEmptyPosEmptyValue(VirtualFrame frame, RAbstractVector vector, RNull value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "!isPosVectorInt"})
        RAbstractVector accessListOnePosEmptyValueList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "!firstPosZero"})
        RAbstractVector accessListOnePosEmptyValueList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractIntVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "firstPosZero"})
        RAbstractVector accessListOnePosZeroEmptyValueList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "!isVectorList", "!isPosVectorInt"})
        RAbstractVector accessListOnePosEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "!isVectorList", "!firstPosZero"})
        RAbstractVector accessListOnePosEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "emptyValue", "!isVectorList", "firstPosZero"})
        RAbstractVector accessListOnePosZeroEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLengthOne", "!isPosVectorInt"})
        RAbstractVector accessListOnePosValueLengthOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLengthOne", "!firstPosZero"})
        RAbstractVector accessListOnePosValueLengthOne(RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            return positions;
        }

        @Specialization(guards = {"onePosition", "valueLengthOne", "firstPosZero"})
        RAbstractVector accessListOnePosZeroValueLengthOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "!isPosVectorInt"})
        RAbstractVector accessListOnePosValueLongerThanOneList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "!firstPosZero"})
        RAbstractVector accessListOnePosValueLongerThanOneList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractIntVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "firstPosZero"})
        RAbstractVector accessListOnePosZeroValueLongerThanOneList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "!isVectorList", "!isPosVectorInt"})
        RAbstractVector accessListOnePosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "!isVectorList", "!firstPosZero"})
        RAbstractVector accessListOnePosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "valueLongerThanOne", "!isVectorList", "firstPosZero"})
        RAbstractVector accessListOnePosZeroValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "!isPosVectorInt"})
        RAbstractVector accessListOnePosEmptyValueList(VirtualFrame frame, RList vector, RNull value, RAbstractVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "!firstPosZero"})
        RAbstractVector accessListOnePosEmptyValueList(VirtualFrame frame, RList vector, RNull value, RAbstractIntVector positions) {
            if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "firstPosZero"})
        RAbstractVector accessListOnePosZeroEmptyValueList(VirtualFrame frame, RList vector, RNull value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_LESS_1);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "!isVectorList", "!isPosVectorInt"})
        RAbstractVector accessListOnePosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RNull value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "!isVectorList", "!firstPosZero"})
        RAbstractVector accessListOnePosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RNull value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"onePosition", "!isVectorList", "firstPosZero"})
        RAbstractVector accessListOnePosZeroValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RNull value, RAbstractIntVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else {
                return positions;
            }
        }

        @Specialization(guards = "multiPos")
        RAbstractVector accessListTwoPosEmptyValueList(VirtualFrame frame, RList vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"multiPos", "emptyValue", "!isVectorList"})
        RAbstractVector accessListTwoPosEmptyValue(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.REPLACEMENT_0);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"multiPos", "valueLengthOne", "!isVectorList"})
        RAbstractVector accessListTwoPosValueLengthOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"multiPos", "valueLongerThanOne", "!isVectorList"})
        RAbstractVector accessListTwoPosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RAbstractVector value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = "multiPos")
        RAbstractVector accessListTwoPosEmptyValueList(VirtualFrame frame, RList vector, RNull value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SELECT_MORE_1);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        @Specialization(guards = {"multiPos", "!isVectorList"})
        RAbstractVector accessListTwoPosValueLongerThanOne(VirtualFrame frame, RAbstractVector vector, RNull value, RAbstractVector positions) {
            if (!isSubset) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.MORE_SUPPLIED_REPLACE);
            } else if (positions.getElementClass() == Object.class) {
                throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
            } else {
                return positions;
            }
        }

        protected boolean singlePosNegative(Object vector, RNull value, RAbstractIntVector p) {
            return p.getLength() == 1 && p.getDataAt(0) < 0 && !RRuntime.isNA(p.getDataAt(0));
        }

        protected boolean singlePosNegative(Object vector, RAbstractVector value, RAbstractIntVector p) {
            return p.getLength() == 1 && p.getDataAt(0) < 0 && !RRuntime.isNA(p.getDataAt(0));
        }

        protected boolean firstPosZero(RAbstractVector vector, RNull value, RAbstractIntVector p) {
            return p.getDataAt(0) == 0;
        }

        protected boolean firstPosZero(RAbstractVector vector, RAbstractVector value, RAbstractIntVector p) {
            return p.getDataAt(0) == 0;
        }

        protected boolean isVectorList(RAbstractVector vector) {
            return vector.getElementClass() == Object.class;
        }

        protected boolean isPosVectorInt(RAbstractVector vector, RAbstractVector value, RAbstractVector p) {
            return p.getElementClass() == RInt.class;
        }

        protected boolean isPosVectorInt(RAbstractVector vector, RNull value, RAbstractVector p) {
            return p.getElementClass() == RInt.class;
        }

        protected boolean isPosVectorInt(RNull vector, RNull value, RAbstractVector p) {
            return p.getElementClass() == RInt.class;
        }

        protected boolean isPosVectorInt(RNull vector, RAbstractVector value, RAbstractVector p) {
            return p.getElementClass() == RInt.class;
        }

        // Truffle DSL bug (?) - guards should work with just RAbstractVector as the vector
        // parameter
        protected boolean onePosition(RList vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() == 1;
        }

        protected boolean onePosition(RList vector, RNull value, RAbstractVector p) {
            return p.getLength() == 1;
        }

        protected boolean noPosition(RList vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() == 0;
        }

        protected boolean noPosition(RList vector, RNull value, RAbstractVector p) {
            return p.getLength() == 0;
        }

        protected boolean multiPos(RList vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RList vector, RNull value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean onePosition(RAbstractVector vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() == 1;
        }

        protected boolean onePosition(RAbstractVector vector, RNull value, RAbstractVector p) {
            return p.getLength() == 1;
        }

        protected boolean noPosition(RAbstractVector vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() == 0;
        }

        protected boolean noPosition(RAbstractVector vector, RNull value, RAbstractVector p) {
            return p.getLength() == 0;
        }

        protected boolean multiPos(RAbstractVector vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RAbstractVector vector, RNull value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RNull vector, RNull value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RNull vector, RAbstractVector value, RAbstractVector p) {
            return p.getLength() > 1;
        }

        protected boolean emptyValue(RNull vector, RAbstractVector value) {
            return value.getLength() == 0;
        }

        protected boolean emptyValue(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() == 0;
        }

        protected boolean valueLengthOne(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() == 1;
        }

        protected boolean valueLongerThanOne(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() > 1;
        }

    }

    @NodeChildren({@NodeChild(value = "newValue", type = RNode.class), @NodeChild(value = "vector", type = RNode.class), @NodeChild(value = "operand", type = RNode.class)})
    public abstract static class CoerceVector extends RNode {

        public abstract Object executeEvaluated(VirtualFrame frame, Object value, Object vector, Object operand);

        @Child private CastComplexNode castComplex;
        @Child private CastDoubleNode castDouble;
        @Child private CastIntegerNode castInteger;
        @Child private CastStringNode castString;
        @Child private CastListNode castList;

        private Object castComplex(VirtualFrame frame, Object vector) {
            if (castComplex == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castComplex = insert(CastComplexNodeFactory.create(null, true, true, true));
            }
            return castComplex.executeCast(frame, vector);
        }

        private Object castDouble(VirtualFrame frame, Object vector) {
            if (castDouble == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castDouble = insert(CastDoubleNodeFactory.create(null, true, true, true));
            }
            return castDouble.executeCast(frame, vector);
        }

        private Object castInteger(VirtualFrame frame, Object vector) {
            if (castInteger == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castInteger = insert(CastIntegerNodeFactory.create(null, true, true, true));
            }
            return castInteger.executeCast(frame, vector);
        }

        private Object castString(VirtualFrame frame, Object vector) {
            if (castString == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castString = insert(CastStringNodeFactory.create(null, true, true, true, false));
            }
            return castString.executeCast(frame, vector);
        }

        private Object castList(VirtualFrame frame, Object vector) {
            if (castList == null) {
                CompilerDirectives.transferToInterpreter();
                castList = insert(CastListNodeFactory.create(null, true, false, true));
            }
            return castList.executeCast(frame, vector);
        }

        @Specialization
        RFunction coerce(VirtualFrame frame, Object value, RFunction vector, Object operand) {
            return vector;
        }

        // int vector value

        @Specialization
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractIntVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractDoubleVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractLogicalVector vector, Object operand) {
            return (RIntVector) castInteger(frame, vector);
        }

        @Specialization
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractStringVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractComplexVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractRawVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "integer", "raw");
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractIntVector value, RList vector, Object operand) {
            return vector;
        }

        // double vector value

        @Specialization
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractIntVector vector, Object operand) {
            return (RDoubleVector) castDouble(frame, vector);
        }

        @Specialization
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractDoubleVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractLogicalVector vector, Object operand) {
            return (RDoubleVector) castDouble(frame, vector);
        }

        @Specialization
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractStringVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractComplexVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractRawVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "double", "raw");
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractDoubleVector value, RList vector, Object operand) {
            return vector;
        }

        // logical vector value

        @Specialization
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractIntVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractDoubleVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractLogicalVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractLogicalVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractStringVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractComplexVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RLogicalVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractRawVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "logical", "raw");
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractLogicalVector value, RList vector, Object operand) {
            return vector;
        }

        // string vector value

        @Specialization
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractIntVector vector, Object operand) {
            return (RStringVector) castString(frame, vector);
        }

        @Specialization
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractDoubleVector vector, Object operand) {
            return (RStringVector) castString(frame, vector);
        }

        @Specialization
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractLogicalVector vector, Object operand) {
            return (RStringVector) castString(frame, vector);
        }

        @Specialization
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractStringVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractComplexVector vector, Object operand) {
            return (RStringVector) castString(frame, vector);
        }

        @Specialization
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractRawVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "character", "raw");
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractStringVector value, RList vector, Object operand) {
            return vector;
        }

        // complex vector value

        @Specialization
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractIntVector vector, Object operand) {
            return (RComplexVector) castComplex(frame, vector);
        }

        @Specialization
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractDoubleVector vector, Object operand) {
            return (RComplexVector) castComplex(frame, vector);
        }

        @Specialization
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractLogicalVector vector, Object operand) {
            return (RComplexVector) castComplex(frame, vector);
        }

        @Specialization
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractStringVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractComplexVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractRawVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "complex", "raw");
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractComplexVector value, RList vector, Object operand) {
            return vector;
        }

        // raw vector value

        @Specialization
        RAbstractRawVector coerce(VirtualFrame frame, RAbstractRawVector value, RAbstractRawVector vector, Object operand) {
            return vector;
        }

        @Specialization(guards = "!isVectorList")
        RRawVector coerce(VirtualFrame frame, RAbstractRawVector value, RAbstractVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "raw", RRuntime.classToString(vector.getElementClass(), false));
        }

        @Specialization
        RList coerce(VirtualFrame frame, RAbstractRawVector value, RList vector, Object operand) {
            return vector;
        }

        // list vector value

        @Specialization
        RList coerce(VirtualFrame frame, RList value, RList vector, Object operand) {
            return vector;
        }

        @Specialization(guards = "!isVectorList")
        RList coerce(VirtualFrame frame, RList value, RAbstractVector vector, Object operand) {
            return (RList) castList(frame, vector);
        }

        // data frame value

        @Specialization
        RList coerce(VirtualFrame frame, RDataFrame value, RAbstractVector vector, Object operand) {
            return (RList) castList(frame, vector);
        }

        // function vector value

        @Specialization
        RFunction coerce(VirtualFrame frame, RFunction value, RAbstractVector vector, Object operand) {
            throw RError.error(frame, getEncapsulatingSourceSection(), RError.Message.SUBASSIGN_TYPE_FIX, "closure", RRuntime.classToString(vector.getElementClass(), false));
        }

        // in all other cases, simply return the vector (no coercion)

        @Specialization
        RNull coerce(RNull value, RNull vector, Object operand) {
            return vector;
        }

        @Specialization
        RNull coerce(RAbstractVector value, RNull vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractVector coerce(RNull value, RAbstractVector vector, Object operand) {
            return vector;
        }

        @Specialization
        RAbstractVector coerce(RList value, RAbstractVector vector, Object operand) {
            return vector;
        }

        protected boolean isVectorList(RAbstractVector value, RAbstractVector vector) {
            return vector.getElementClass() == Object.class;
        }

    }
}
