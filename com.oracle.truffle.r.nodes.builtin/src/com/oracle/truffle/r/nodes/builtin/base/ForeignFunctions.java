/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.library.graphics.*;
import com.oracle.truffle.r.library.methods.*;
import com.oracle.truffle.r.library.stats.*;
import com.oracle.truffle.r.library.tools.*;
import com.oracle.truffle.r.library.utils.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.ffi.*;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolInfo;

/**
 * {@code .C}, {@code .Call} {@code .Fortran}, {@code .External}, {@code .External2},
 * {@code External.graphics} functions.
 *
 * TODO Completeness (more types, more error checks), Performance (copying). Especially all the
 * subtleties around copying.
 *
 * See <a href="https://stat.ethz.ch/R-manual/R-devel/library/base/html/Foreign.html">here</a>.
 */
public class ForeignFunctions {
    public abstract static class FortranCAdapter extends CastAdapter {

        protected final BranchProfile errorProfile = BranchProfile.create();

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RRuntime.LOGICAL_FALSE, RRuntime.LOGICAL_FALSE, RMissing.instance, RMissing.instance};
        }

        protected int[] checkNAs(int argIndex, int[] data) {
            for (int i = 0; i < data.length; i++) {
                if (RRuntime.isNA(data[i])) {
                    errorProfile.enter();
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.NA_IN_FOREIGN_FUNCTION_CALL, argIndex);
                }
            }
            return data;
        }

        protected double[] checkNAs(int argIndex, double[] data) {
            for (int i = 0; i < data.length; i++) {
                if (!RRuntime.isFinite(data[i])) {
                    errorProfile.enter();
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.NA_NAN_INF_IN_FOREIGN_FUNCTION_CALL, argIndex);
                }
            }
            return data;
        }
    }

    /**
     * For now, just some special case functions that are built in to the implementation.
     */
    @RBuiltin(name = ".Fortran", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "NAOK", "DUP", "PACKAGE", "ENCODING"})
    public abstract static class Fortran extends FortranCAdapter {
        private static final String E = RRuntime.NAMES_ATTR_EMPTY_VALUE;
        private static final RStringVector DQRDC2_NAMES = RDataFactory.createStringVector(new String[]{"qr", E, E, E, E, "rank", "qraux", "pivot", E}, RDataFactory.COMPLETE_VECTOR);

        @SuppressWarnings("unused")
        @Specialization(guards = "dqrdc2(f)")
        protected RList fortranDqrdc2(RList f, RArgsValuesAndNames args, byte naok, byte dup, RMissing rPackage, RMissing encoding) {
            controlVisibility();
            Object[] argValues = args.getValues();
            try {
                RDoubleVector xVec = (RDoubleVector) argValues[0];
                int ldx = (int) argValues[1];
                int n = (int) argValues[2];
                int p = (int) argValues[3];
                double tol = (double) argValues[4];
                RIntVector rankVec = (RIntVector) argValues[5];
                RDoubleVector qrauxVec = (RDoubleVector) argValues[6];
                RIntVector pivotVec = (RIntVector) argValues[7];
                RDoubleVector workVec = (RDoubleVector) argValues[8];
                double[] x = xVec.getDataTemp();
                int[] rank = rankVec.getDataTemp();
                double[] qraux = qrauxVec.getDataTemp();
                int[] pivot = pivotVec.getDataTemp();
                RFFIFactory.getRFFI().getRDerivedRFFI().dqrdc2(x, ldx, n, p, tol, rank, qraux, pivot, workVec.getDataCopy());
                // @formatter:off
                Object[] data = new Object[]{
                            RDataFactory.createDoubleVector(x, RDataFactory.COMPLETE_VECTOR, xVec.getDimensions()),
                            argValues[1], argValues[2], argValues[3], argValues[4],
                            RDataFactory.createIntVector(rank, RDataFactory.COMPLETE_VECTOR),
                            RDataFactory.createDoubleVector(qraux, RDataFactory.COMPLETE_VECTOR),
                            RDataFactory.createIntVector(pivot, RDataFactory.COMPLETE_VECTOR),
                            argValues[8]
                };
                // @formatter:on
                return RDataFactory.createList(data, DQRDC2_NAMES);
            } catch (ClassCastException | ArrayIndexOutOfBoundsException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INCORRECT_ARG, "dqrdc2");
            }
        }

        public boolean dqrdc2(RList f) {
            return matchName(f, "dqrdc2");
        }

        private static final RStringVector DQRCF_NAMES = RDataFactory.createStringVector(new String[]{E, E, E, E, E, E, "coef", "info"}, RDataFactory.COMPLETE_VECTOR);

        @SuppressWarnings("unused")
        @Specialization(guards = "dqrcf(f)")
        protected RList fortranDqrcf(RList f, RArgsValuesAndNames args, byte naok, byte dup, RMissing rPackage, RMissing encoding) {
            controlVisibility();
            Object[] argValues = args.getValues();
            try {
                RDoubleVector xVec = (RDoubleVector) argValues[0];
                int n = (int) argValues[1];
                RIntVector k = (RIntVector) argValues[2];
                RDoubleVector qrauxVec = (RDoubleVector) argValues[3];
                RDoubleVector yVec = (RDoubleVector) argValues[4];
                int ny = (int) argValues[5];
                RDoubleVector bVec = (RDoubleVector) argValues[6];
                RIntVector infoVec = (RIntVector) argValues[7];
                double[] x = xVec.getDataTemp();
                double[] qraux = qrauxVec.getDataTemp();
                double[] y = yVec.getDataTemp();
                double[] b = bVec.getDataTemp();
                int[] info = infoVec.getDataTemp();
                RFFIFactory.getRFFI().getRDerivedRFFI().dqrcf(x, n, k.getDataAt(0), qraux, y, ny, b, info);
                RDoubleVector coef = RDataFactory.createDoubleVector(b, RDataFactory.COMPLETE_VECTOR);
                coef.copyAttributesFrom(attrProfiles, bVec);
                // @formatter:off
                Object[] data = new Object[]{
                            RDataFactory.createDoubleVector(x, RDataFactory.COMPLETE_VECTOR),
                            argValues[1],
                            k.copy(),
                            RDataFactory.createDoubleVector(qraux, RDataFactory.COMPLETE_VECTOR),
                            RDataFactory.createDoubleVector(y, RDataFactory.COMPLETE_VECTOR),
                            argValues[5],
                            coef,
                            RDataFactory.createIntVector(info, RDataFactory.COMPLETE_VECTOR),
                };
                // @formatter:on
                return RDataFactory.createList(data, DQRCF_NAMES);

            } catch (ClassCastException | ArrayIndexOutOfBoundsException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INCORRECT_ARG, "dqrcf");
            }
        }

        public boolean dqrcf(RList f) {
            return matchName(f, "dqrcf");
        }

    }

    @RBuiltin(name = ".C", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "NAOK", "DUP", "PACKAGE", "ENCODING"})
    public abstract static class C extends FortranCAdapter {

        private static final int SCALAR_DOUBLE = 0;
        private static final int SCALAR_INT = 1;
        private static final int SCALAR_LOGICAL = 2;
        @SuppressWarnings("unused") private static final int SCALAR_STRING = 3;
        private static final int VECTOR_DOUBLE = 10;
        private static final int VECTOR_INT = 11;
        private static final int VECTOR_LOGICAL = 12;
        @SuppressWarnings("unused") private static final int VECTOR_STRING = 12;

        @SuppressWarnings("unused")
        @Specialization
        protected RList c(String f, RArgsValuesAndNames args, byte naok, byte dup, RMissing rPackage, RMissing encoding) {
            controlVisibility();
            Object[] argValues = args.getValues();
            SymbolInfo symbolInfo = DLL.findSymbolInfo(f, null);
            if (symbolInfo == null) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.C_SYMBOL_NOT_IN_TABLE, f);
            }
            boolean dupArgs = RRuntime.fromLogical(dup);
            boolean checkNA = RRuntime.fromLogical(naok);
            // Analyze the args, making copies (ignoring dup for now)
            int[] argTypes = new int[argValues.length];
            Object[] nativeArgs = new Object[argValues.length];
            for (int i = 0; i < argValues.length; i++) {
                Object arg = argValues[i];
                if (arg instanceof RDoubleVector) {
                    argTypes[i] = VECTOR_DOUBLE;
                    nativeArgs[i] = checkNAs(i + 1, ((RDoubleVector) arg).getDataCopy());
                } else if (arg instanceof RIntVector) {
                    argTypes[i] = VECTOR_INT;
                    nativeArgs[i] = checkNAs(i + 1, ((RIntVector) arg).getDataCopy());
                } else if (arg instanceof RLogicalVector) {
                    argTypes[i] = VECTOR_LOGICAL;
                    // passed as int[]
                    byte[] data = ((RLogicalVector) arg).getDataWithoutCopying();
                    int[] dataAsInt = new int[data.length];
                    for (int j = 0; j < data.length; j++) {
                        // An NA is an error but the error handling happens in checkNAs
                        dataAsInt[j] = RRuntime.isNA(data[j]) ? RRuntime.INT_NA : data[j];
                    }
                    nativeArgs[i] = checkNAs(i + 1, dataAsInt);
                } else if (arg instanceof Double) {
                    argTypes[i] = SCALAR_DOUBLE;
                    nativeArgs[i] = checkNAs(i + 1, new double[]{(double) arg});
                } else if (arg instanceof Integer) {
                    argTypes[i] = SCALAR_INT;
                    nativeArgs[i] = checkNAs(i + 1, new int[]{(int) arg});
                } else if (arg instanceof Byte) {
                    argTypes[i] = SCALAR_LOGICAL;
                    nativeArgs[i] = checkNAs(i + 1, new int[]{RRuntime.isNA((byte) arg) ? RRuntime.INT_NA : (byte) arg});
                } else {
                    errorProfile.enter();
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNIMPLEMENTED_ARG_TYPE, i + 1);
                }
            }
            try {
                RFFIFactory.getRFFI().getCRFFI().invoke(symbolInfo, nativeArgs);
            } catch (Throwable t) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.NATIVE_CALL_FAILED, t.getMessage());
            }
            // we have to assume that the native method updated everything
            RStringVector listNames = validateArgNames(argValues.length, getSuppliedSignature());
            Object[] results = new Object[argValues.length];
            for (int i = 0; i < argValues.length; i++) {
                switch (argTypes[i]) {
                    case SCALAR_DOUBLE:
                        results[i] = RDataFactory.createDoubleVector((double[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                        break;
                    case SCALAR_INT:
                        results[i] = RDataFactory.createIntVector((int[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                        break;
                    case SCALAR_LOGICAL:
                        results[i] = RDataFactory.createLogicalVector((byte[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                        break;
                    case VECTOR_DOUBLE: {
                        results[i] = ((RDoubleVector) argValues[i]).copyResetData((double[]) nativeArgs[i]);
                        break;
                    }
                    case VECTOR_INT: {
                        results[i] = ((RIntVector) argValues[i]).copyResetData((int[]) nativeArgs[i]);
                        break;
                    }
                    case VECTOR_LOGICAL: {
                        results[i] = ((RLogicalVector) argValues[i]).copyResetData((byte[]) nativeArgs[i]);
                        break;
                    }

                }
            }
            return RDataFactory.createList(results, listNames);
        }

        private static RStringVector validateArgNames(int argsLength, ArgumentsSignature signature) {
            String[] listArgNames = new String[argsLength];
            for (int i = 0; i < argsLength; i++) {
                String name = signature.getName(i + 1);
                if (name == null) {
                    name = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                }
                listArgNames[i] = name;
            }
            return RDataFactory.createStringVector(listArgNames, RDataFactory.COMPLETE_VECTOR);
        }

    }

    /**
     * Handles the generic case, but also many special case functions that are called from the
     * default packages.
     */
    @RBuiltin(name = ".Call", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"})
    public abstract static class DotCall extends CastAdapter {

        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile zVecLgt1 = ConditionProfile.createBinaryProfile();
        private final ConditionProfile noDims = ConditionProfile.createBinaryProfile();

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RMissing.instance};
        }

        // TODO: handle more argument types (this is sufficient to run the b25 benchmarks)
        @SuppressWarnings("unused")
        @Specialization(guards = "fft(f)")
        protected RComplexVector callFFT(VirtualFrame frame, RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            RComplexVector zVec = castComplexVector(frame, castVector(frame, argValues[0]));
            double[] z = zVec.getDataTemp();
            byte inverse = castLogical(frame, castVector(frame, argValues[1]));
            int inv = RRuntime.isNA(inverse) || inverse == RRuntime.LOGICAL_FALSE ? -2 : 2;
            int retCode = 7;
            if (zVecLgt1.profile(zVec.getLength() > 1)) {
                int[] maxf = new int[1];
                int[] maxp = new int[1];
                if (noDims.profile(zVec.getDimensions() == null)) {
                    int n = zVec.getLength();
                    RFFIFactory.getRFFI().getRDerivedRFFI().fft_factor(n, maxf, maxp);
                    if (maxf[0] == 0) {
                        errorProfile.enter();
                        throw RError.error(getEncapsulatingSourceSection(), RError.Message.FFT_FACTORIZATION);
                    }
                    double[] work = new double[4 * maxf[0]];
                    int[] iwork = new int[maxp[0]];
                    retCode = RFFIFactory.getRFFI().getRDerivedRFFI().fft_work(z, 1, n, 1, inv, work, iwork);
                } else {
                    int maxmaxf = 1;
                    int maxmaxp = 1;
                    int[] d = zVec.getDimensions();
                    int ndims = d.length;
                    /* do whole loop just for error checking and maxmax[fp] .. */
                    for (int i = 0; i < ndims; i++) {
                        if (d[i] > 1) {
                            RFFIFactory.getRFFI().getRDerivedRFFI().fft_factor(d[i], maxf, maxp);
                            if (maxf[0] == 0) {
                                errorProfile.enter();
                                throw RError.error(getEncapsulatingSourceSection(), RError.Message.FFT_FACTORIZATION);
                            }
                            if (maxf[0] > maxmaxf) {
                                maxmaxf = maxf[0];
                            }
                            if (maxp[0] > maxmaxp) {
                                maxmaxp = maxp[0];
                            }
                        }
                    }
                    double[] work = new double[4 * maxmaxf];
                    int[] iwork = new int[maxmaxp];
                    int nseg = zVec.getLength();
                    int n = 1;
                    int nspn = 1;
                    for (int i = 0; i < ndims; i++) {
                        if (d[i] > 1) {
                            nspn *= n;
                            n = d[i];
                            nseg /= n;
                            RFFIFactory.getRFFI().getRDerivedRFFI().fft_factor(n, maxf, maxp);
                            RFFIFactory.getRFFI().getRDerivedRFFI().fft_work(z, nseg, n, nspn, inv, work, iwork);
                        }
                    }

                }
            }

            return RDataFactory.createComplexVector(z, zVec.isComplete(), zVec.getDimensions());
        }

        public boolean fft(RList f) {
            return matchName(f, "fft");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "initMethodDispatch(f)")
        @TruffleBoundary
        protected REnvironment initMethodDispatch(RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            REnvironment env = (REnvironment) argValues[0];
            // TBD what should we actually do here
            return MethodsListDispatch.getInstance().initMethodDispatch(env);
        }

        public boolean initMethodDispatch(RList f) {
            return matchName(f, "R_initMethodDispatch");
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = "methodsPackageMetaName(f)")
        protected String callMethodsPackageMetaName(RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            // TODO proper error checks
            String prefixString = (String) argValues[0];
            String nameString = (String) argValues[1];
            String pkgString = (String) argValues[2];
            return MethodsListDispatch.getInstance().methodsPackageMetaName(prefixString, nameString, pkgString);
        }

        public boolean methodsPackageMetaName(RList f) {
            return matchName(f, "R_methodsPackageMetaName");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isSetPrimitiveMethods(f)")
        @TruffleBoundary
        protected Object setPrimitiveMethods(RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            // TODO proper error checks
            String fname = RRuntime.asString(argValues[0]);
            Object op = argValues[1];
            String codeVec = RRuntime.asString(argValues[2]);
            RFunction fundef = (RFunction) argValues[3];
            Object mlist = argValues[4];
            return SetPrimitiveMethods.doit(fname, op, codeVec, fundef, mlist);
        }

        public boolean isSetPrimitiveMethods(RList f) {
            return matchName(f, "R_M_setPrimitiveMethods");
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = "getClassFromCache(f)")
        protected Object callGetClassFromCache(RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            REnvironment table = (REnvironment) argValues[1];
            String klassString = RRuntime.asString(argValues[0]);
            if (klassString != null) {
                return MethodsListDispatch.getInstance().getClassFromCache(table, klassString);
            } else {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARG_TYPE);
            }
        }

        public boolean getClassFromCache(RList f) {
            return matchName(f, "R_getClassFromCache");
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = "setMethodDispatch(f)")
        protected Object callSetMethodDispatch(RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            byte onOff = (byte) argValues[0];
            return MethodsListDispatch.getInstance().setMethodDispatch(onOff);
        }

        public boolean setMethodDispatch(RList f) {
            return matchName(f, "R_set_method_dispatch");
        }

        @Specialization
        public Object callNamedFunction(String name, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            return callNamedFunctionWithPackage(name, args, null);
        }

        @Specialization
        public Object callNamedFunctionWithPackage(String name, RArgsValuesAndNames args, String packageName) {
            SymbolInfo symbolInfo = DLL.findSymbolInfo(name, packageName);
            if (symbolInfo == null) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), Message.C_SYMBOL_NOT_IN_TABLE, name);
            }
            try {
                return RFFIFactory.getRFFI().getCallRFFI().invokeCall(symbolInfo, args.getValues());
            } catch (Throwable t) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.NATIVE_CALL_FAILED, t.getMessage());
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isFlushconsole(f)")
        protected RNull flushConsole(RList f, RArgsValuesAndNames args, RMissing packageName) {
            return RNull.instance;
        }

        public boolean isFlushconsole(RList f) {
            return matchName(f, "flushconsole");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isCrc64(f)")
        protected String crc64(RList f, RArgsValuesAndNames args, RMissing packageName) {
            Object[] argValues = args.getValues();
            String input = RRuntime.asString(argValues[0]);
            return Crc64.crc64(input);
        }

        public boolean isCrc64(RList f) {
            return matchName(f, "crc64");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isMenu(f)")
        @TruffleBoundary
        protected int menu(RList f, RArgsValuesAndNames args, RMissing packageName) {
            Object[] values = args.getValues();
            String[] choices;
            if (values[0] instanceof String) {
                choices = new String[]{(String) values[0]};
            } else if (values[0] instanceof RStringVector) {
                choices = ((RStringVector) values[0]).getDataWithoutCopying();
            } else {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "choices");
            }
            return Menu.menu(choices);
        }

        public boolean isMenu(RList f) {
            return matchName(f, "menu");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isCairoProps(f)")
        protected byte cairoProps(RList f, RArgsValuesAndNames args, RMissing packageName) {
            return RRuntime.LOGICAL_FALSE;
        }

        public boolean isCairoProps(RList f) {
            return matchName(f, "cairoProps");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isMakeQuartzDefault(f)")
        protected byte makeQuartzDefault(RList f, RArgsValuesAndNames args, RMissing packageName) {
            return RRuntime.LOGICAL_FALSE;
        }

        public boolean isMakeQuartzDefault(RList f) {
            return matchName(f, "makeQuartzDefault");
        }

        @Specialization(guards = "isCor(f)")
        protected Object doCor(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            return doCovCor(frame, false, args);
        }

        public boolean isCor(RList f) {
            return matchName(f, "cor");
        }

        @Specialization(guards = "isCov(f)")
        protected Object doCov(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            return doCovCor(frame, true, args);
        }

        public boolean isCov(RList f) {
            return matchName(f, "cov");
        }

        private Object doCovCor(VirtualFrame frame, boolean isCov, RArgsValuesAndNames args) {
            controlVisibility();
            Object[] argValues = args.getValues();
            if (argValues[0] == RNull.instance) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.IS_NULL, "x");
            }
            // TODO error checks/coercions
            RDoubleVector x = (RDoubleVector) argValues[0];
            RDoubleVector y = argValues[1] == RNull.instance ? null : (RDoubleVector) argValues[1];
            int method = ((RIntVector) argValues[2]).getDataAt(0);
            if (method != 4) {
                throw RError.nyi(getEncapsulatingSourceSection(), "method");
            }
            boolean iskendall = RRuntime.fromLogical(castLogical(frame, castVector(frame, argValues[3])));
            return Covcor.getInstance().corcov(x, y, method, iskendall, !isCov, getEncapsulatingSourceSection());

        }

        @Specialization(guards = "isSplineCoef(f)")
        protected RList splineCoef(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            Object[] argValues = args.getValues();
            int method = castInt(frame, castVector(frame, argValues[0]));
            RDoubleVector x = (RDoubleVector) castVector(frame, argValues[1]);
            RDoubleVector y = (RDoubleVector) castVector(frame, argValues[2]);
            return SplineFunctions.splineCoef(method, x, y);
        }

        public boolean isSplineCoef(RList f) {
            return matchName(f, "SplineCoef");
        }

        @Specialization(guards = "isSplineEval(f)")
        protected RDoubleVector splineEval(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            Object[] argValues = args.getValues();
            RDoubleVector xout = (RDoubleVector) castVector(frame, argValues[0]);
            // This is called with the result of SplineCoef, so it is surely an RList
            RList z = (RList) argValues[1];
            return SplineFunctions.splineEval(attrProfiles, xout, z);
        }

        public boolean isSplineEval(RList f) {
            return matchName(f, "SplineEval");
        }

        @Specialization(guards = "isDoTabExpand(f)")
        protected RStringVector tabExpand(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            Object[] argValues = args.getValues();
            RStringVector strings = (RStringVector) castVector(frame, argValues[0]);
            RIntVector starts = (RIntVector) castVector(frame, argValues[1]);
            return Text.doTabExpand(strings, starts);
        }

        public boolean isDoTabExpand(RList f) {
            return matchName(f, "doTabExpand");
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object dotCallFallback(Object fobj, Object args, Object packageName) {
            String name = null;
            if (fobj instanceof RList) {
                RList f = (RList) fobj;
                RStringVector names = f.getNames(attrProfiles);
                for (int i = 0; i < names.getLength(); i++) {
                    if (names.getDataAt(i).equals("name")) {
                        name = (String) f.getDataAt(i);
                        break;
                    }
                }
            }
            throw new RInternalError(".Call specialization failure: %s ", name == null ? "<unknown>" : name);
        }

    }

    private static String isString(Object arg) {
        if (arg instanceof String) {
            return (String) arg;
        } else if (arg instanceof RStringVector) {
            if (((RStringVector) arg).getLength() == 0) {
                return null;
            } else {
                return ((RStringVector) arg).getDataAt(0);
            }
        } else {
            return null;
        }
    }

    /**
     * Casts for use on value elements of {@link RArgsValuesAndNames}. Since the starting value
     * could a scalar, first use {@link #castVector}.
     */
    protected abstract static class CastAdapter extends RBuiltinNode {
        @Child private CastLogicalNode castLogical;
        @Child private CastIntegerNode castInt;
        @Child private CastDoubleNode castDouble;
        @Child private CastComplexNode castComplex;
        @Child private CastToVectorNode castVector;

        protected final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

        protected byte castLogical(VirtualFrame frame, RAbstractVector operand) {
            if (castLogical == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castLogical = insert(CastLogicalNodeGen.create(null, false, false, false));
            }
            return ((RLogicalVector) castLogical.executeCast(frame, operand)).getDataAt(0);
        }

        protected int castInt(VirtualFrame frame, RAbstractVector operand) {
            if (castInt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castInt = insert(CastIntegerNodeGen.create(null, false, false, false));
            }
            return ((RIntVector) castInt.executeCast(frame, operand)).getDataAt(0);
        }

        protected RDoubleVector castDouble(VirtualFrame frame, RAbstractVector operand) {
            if (castDouble == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castDouble = insert(CastDoubleNodeGen.create(null, false, false, false));
            }
            return (RDoubleVector) castDouble.executeCast(frame, operand);
        }

        protected RComplexVector castComplexVector(VirtualFrame frame, Object operand) {
            if (castComplex == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castComplex = insert(CastComplexNodeGen.create(null, true, true, false));
            }
            return (RComplexVector) castComplex.executeCast(frame, operand);
        }

        protected RAbstractVector castVector(VirtualFrame frame, Object value) {
            if (castVector == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castVector = insert(CastToVectorNodeGen.create(null, false, false, false, false));
            }
            return (RAbstractVector) castVector.executeObject(frame, value);
        }

        /**
         * This is an inefficient guard but it matters little unless there are many different calls
         * being made within the same evaluation. A {@code NativeSymbolInfo} object would provide a
         * more efficient basis.
         */
        protected boolean matchName(RList f, String name) {
            if (f.getNames(attrProfiles) == null) {
                return false;
            }
            RStringVector names = f.getNames(attrProfiles);
            for (int i = 0; i < names.getLength(); i++) {
                if (names.getDataAt(i).equals("name")) {
                    return f.getDataAt(i).equals(name) ? true : false;
                }
            }
            return false;
        }

    }

    @RBuiltin(name = ".External", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "PACKAGE"})
    public abstract static class DotExternal extends CastAdapter {

        private final BranchProfile errorProfile = BranchProfile.create();

        // Transcribed from GnuR, library/utils/src/io.c
        @SuppressWarnings("unused")
        @Specialization(guards = "isCountFields(f)")
        protected Object countFields(VirtualFrame frame, RList f, RArgsValuesAndNames args, RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            RConnection conn = (RConnection) argValues[0];
            Object sepArg = argValues[1];
            char sepChar;
            Object quoteArg = argValues[2];
            int nskip = castInt(frame, castVector(frame, argValues[3]));
            byte blskip = castLogical(frame, castVector(frame, argValues[4]));
            String commentCharArg = isString(argValues[5]);
            char comChar;
            if (!(commentCharArg != null && commentCharArg.length() == 1)) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "comment.char");
            } else {
                comChar = commentCharArg.charAt(0);
            }

            if (nskip < 0 || nskip == RRuntime.INT_NA) {
                nskip = 0;
            }
            if (blskip == RRuntime.LOGICAL_NA) {
                blskip = RRuntime.LOGICAL_TRUE;
            }

            if (sepArg instanceof RNull) {
                sepChar = 0;
            } else {
                String s = isString(sepArg);
                if (s == null) {
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "sep");
                } else {
                    if (s.length() == 0) {
                        sepChar = 0;
                    } else {
                        sepChar = s.charAt(0);
                    }
                }
            }
            String quoteSet;
            if (quoteArg instanceof RNull) {
                quoteSet = "";
            } else {
                String s = isString(quoteArg);
                if (s == null) {
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, "invalid quote symbol set");
                } else {
                    quoteSet = s;
                }
            }
            try (RConnection openConn = conn.forceOpen("r")) {
                return CountFields.execute(openConn, sepChar, quoteSet, nskip, RRuntime.fromLogical(blskip), comChar);
            } catch (IllegalStateException | IOException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, ex.getMessage());
            }
        }

        public boolean isCountFields(RList f) {
            return matchName(f, "countfields");
        }

        @Specialization(guards = "isReadTableHead(f)")
        protected Object doReadTableHead(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            // TODO This is quite incomplete and just uses readLines, which works for some inputs
            controlVisibility();
            Object[] argValues = args.getValues();
            RConnection conn = (RConnection) argValues[0];
            int nlines = castInt(frame, castVector(frame, argValues[1]));
            try (RConnection openConn = conn.forceOpen("r")) {
                return RDataFactory.createStringVector(openConn.readLines(nlines), RDataFactory.COMPLETE_VECTOR);
            } catch (IOException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.ERROR_READING_CONNECTION, ex.getMessage());
            }
        }

        public boolean isReadTableHead(RList f) {
            return matchName(f, "readtablehead");
        }

        @Specialization(guards = "isRnorm(f)")
        protected Object doRnorm(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            int n = castInt(frame, castVector(frame, argValues[0]));
            // TODO full error checks
            double mean = (double) argValues[1];
            double standardd = (double) argValues[2];
            return Random2.rnorm(n, mean, standardd);
        }

        public boolean isRnorm(RList f) {
            return matchName(f, "rnorm");
        }

        @Specialization(guards = "isRunif(f)")
        protected Object doRunif(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            // TODO full error checks
            int n = castInt(frame, castVector(frame, argValues[0]));
            double min = (castDouble(frame, castVector(frame, argValues[1]))).getDataAt(0);
            double max = (castDouble(frame, castVector(frame, argValues[2]))).getDataAt(0);
            return Runif.runif(n, min, max);
        }

        public boolean isRunif(RList f) {
            return matchName(f, "runif");
        }

        @Specialization(guards = "isQgamma(f)")
        protected RDoubleVector doQgamma(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            RDoubleVector p = (RDoubleVector) castVector(frame, argValues[0]);
            RDoubleVector shape = (RDoubleVector) castVector(frame, argValues[1]);
            RDoubleVector scale = (RDoubleVector) castVector(frame, argValues[2]);
            if (shape.getLength() == 0 || scale.getLength() == 0) {
                return RDataFactory.createEmptyDoubleVector();
            }
            byte lowerTail = castLogical(frame, castVector(frame, argValues[3]));
            byte logP = castLogical(frame, castVector(frame, argValues[4]));
            return GammaFunctions.Qgamma.getInstance().qgamma(p, shape, scale, lowerTail, logP, attrProfiles);
        }

        public boolean isQgamma(RList f) {
            return matchName(f, "qgamma");
        }

        @Specialization(guards = "isDownload(f)")
        protected int doDownload(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args, @SuppressWarnings("unused") RMissing packageName) {
            controlVisibility();
            Object[] argValues = args.getValues();
            String url = isString(argValues[0]);
            String destFile = isString(argValues[1]);
            byte quiet = castLogical(frame, castVector(frame, argValues[2]));
            String mode = isString(argValues[3]);
            byte cacheOK = castLogical(frame, castVector(frame, argValues[4]));
            if (url == null || destFile == null || mode == null) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_UNNAMED_ARGUMENTS);
            }
            try {
                Download.download(url, destFile, RRuntime.fromLogical(quiet), mode, RRuntime.fromLogical(cacheOK));
                return 0;
            } catch (IOException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, ex.getMessage());
            }
        }

        public boolean isDownload(RList f) {
            return matchName(f, "download");
        }

    }

    @RBuiltin(name = ".External2", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "..."})
    public abstract static class DotExternal2 extends CastAdapter {

        private final BranchProfile errorProfile = BranchProfile.create();

        // Transcribed from GnuR, library/utils/src/io.c
        @Specialization(guards = "isWriteTable(f)")
        protected Object doWriteTable(VirtualFrame frame, @SuppressWarnings("unused") RList f, RArgsValuesAndNames args) {
            controlVisibility();
            Object[] argValues = args.getValues();
            Object conArg = argValues[1];
            RConnection conn;
            if (!(conArg instanceof RConnection)) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, "'file' is not a connection");
            } else {
                conn = (RConnection) conArg;
            }
            // TODO check connection writeable

            int nr = castInt(frame, castVector(frame, argValues[2]));
            int nc = castInt(frame, castVector(frame, argValues[3]));
            Object rnamesArg = argValues[4];
            Object sepArg = argValues[5];
            Object eolArg = argValues[6];
            Object naArg = argValues[7];
            Object decArg = argValues[8];
            Object quoteArg = argValues[9];
            byte qmethod = castLogical(frame, castVector(frame, argValues[10]));

            String csep;
            String ceol;
            String cna;
            String cdec;

            if (nr == RRuntime.INT_NA) {
                invalidArgument("nr");
            }
            if (nc == RRuntime.INT_NA) {
                invalidArgument("nc");
            }
            if (!(rnamesArg instanceof RNull) && isString(rnamesArg) == null) {
                invalidArgument("rnames");
            }
            if ((csep = isString(sepArg)) == null) {
                invalidArgument("sep");
            }
            if ((ceol = isString(eolArg)) == null) {
                invalidArgument("eol");
            }
            if ((cna = isString(naArg)) == null) {
                invalidArgument("na");
            }
            if ((cdec = isString(decArg)) == null) {
                invalidArgument("dec");
            }
            if (qmethod == RRuntime.LOGICAL_NA) {
                invalidArgument("qmethod");
            }
            if (cdec.length() != 1) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, "'dec' must be a single character");
            }
            boolean[] quoteCol = new boolean[nc];
            boolean quoteRn = false;
            RIntVector quote = (RIntVector) castVector(frame, quoteArg);
            for (int i = 0; i < quote.getLength(); i++) {
                int qi = quote.getDataAt(i);
                if (qi == 0) {
                    quoteRn = true;
                }
                if (qi > 0) {
                    quoteCol[qi - 1] = true;
                }
            }
            try (RConnection openConn = conn.forceOpen("wt")) {
                WriteTable.execute(openConn, argValues[0], nr, nc, rnamesArg, csep, ceol, cna, cdec.charAt(0), RRuntime.fromLogical(qmethod), quoteCol, quoteRn);
            } catch (IOException | IllegalArgumentException ex) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, ex.getMessage());
            }
            return RNull.instance;
        }

        protected void invalidArgument(String name) throws RError {
            errorProfile.enter();
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, name);
        }

        public boolean isWriteTable(RList f) {
            return matchName(f, "writetable");
        }

        @TruffleBoundary
        @Specialization(guards = "isTypeConvert(f)")
        protected Object doTypeConvert(@SuppressWarnings("unused") RList f, RArgsValuesAndNames args) {
            controlVisibility();
            Object[] argValues = args.getValues();
            RAbstractStringVector x = (RAbstractStringVector) argValues[0];
            RAbstractStringVector naStrings = (RAbstractStringVector) argValues[1];
            byte asIs = (byte) argValues[2];
            String numeral = RRuntime.asString(argValues[3]);
            return TypeConvert.typeConvert(x, naStrings, asIs, numeral);
        }

        public boolean isTypeConvert(RList f) {
            return matchName(f, "typeconvert");
        }

        @TruffleBoundary
        @Specialization(guards = "isPar(f)")
        protected Object doPar(@SuppressWarnings("unused") RList f, RArgsValuesAndNames args) {
            controlVisibility();
            return GraphicsCCalls.par(args);
        }

        public boolean isPar(RList f) {
            return matchName(f, "C_par");
        }
    }

    @RBuiltin(name = ".External.graphics", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "..."})
    public abstract static class DotExternalGraphics extends CastAdapter {
        @TruffleBoundary
        @Specialization(guards = "isPlotXY(f)")
        protected RNull doPlotXY(@SuppressWarnings("unused") RList f, RArgsValuesAndNames args) {
            controlVisibility();
            GraphicsCCalls.plotXy((RDoubleVector) args.getValues()[0]);
            return RNull.instance;
        }

        public boolean isPlotXY(RList f) {
            return matchName(f, "C_plotXY");
        }

    }

}
