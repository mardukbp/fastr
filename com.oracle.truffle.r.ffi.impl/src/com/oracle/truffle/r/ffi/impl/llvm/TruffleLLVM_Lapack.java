/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.llvm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.ffi.impl.common.LibPaths;
import com.oracle.truffle.r.ffi.impl.interop.NativeDoubleArray;
import com.oracle.truffle.r.ffi.impl.interop.NativeIntegerArray;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.DLL;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.DLLRFFI;
import com.oracle.truffle.r.runtime.ffi.LapackRFFI;

/**
 * When the embedded GNU R is built, LLVM is created for the components of the {@code libRblas} and
 * {@code libRlapack} libraries. In principle we could call the subroutines direct but since,
 * Fortran passes every argument by reference we would have to create many length 1 arrays to wrap
 * the scalar arguments. So we call through a thing veneer in {@code lapack_rffi.c} that forwards
 * the call taking the address of the scalar arguments. We also take the liberty of defining the
 * {@code info} argument taken my most all if the functions in the veneer, and returning the value
 * as the result of the call.
 *
 * N.B. The usual implicit loading of {@code libRlapack} and {@code libRblas} that we get with
 * native {@code dlopen} via {@code libR} does not happen with LLVM, so we must force their loading
 * when this API is requested.
 *
 */
public class TruffleLLVM_Lapack implements LapackRFFI {

    TruffleLLVM_Lapack() {
        /*
         * This is a workaround for bad LLVM generated by DragonEgg for (some) of the Lapack
         * functions; additional spurious arguments. Unfortunately for this to be portable we would
         * have to load libR natively to get the rpath support. This code is OS X specific and
         * depends on specific versions.
         */
        RootCallTarget callTarget;
        boolean useLLVM = System.getenv("FASTR_LLVM_LAPACK") != null;
        if (useLLVM) {
            callTarget = openLLVMLibraries();
        } else {
            callTarget = openNativeLibraries();
            callTarget.call(LibPaths.getBuiltinLibPath("gcc_s.1"), false, true);
            callTarget.call(LibPaths.getBuiltinLibPath("quadmath.0"), false, true);
            callTarget.call(LibPaths.getBuiltinLibPath("gfortran.3"), false, true);
        }
        callTarget.call(LibPaths.getBuiltinLibPath("Rblas"), false, true);
        callTarget.call(LibPaths.getBuiltinLibPath("Rlapack"), false, true);
    }

    private static RootCallTarget openLLVMLibraries() {
        return DLLRFFI.DLOpenRootNode.create(RContext.getInstance());
    }

    private static RootCallTarget openNativeLibraries() {
        TruffleLLVM_NativeDLL.NativeDLOpenRootNode rootNode = TruffleLLVM_NativeDLL.NativeDLOpenRootNode.create();
        return rootNode.getCallTarget();
    }

    private static class TruffleLLVM_IlaverNode extends IlaverNode {
        @Child private Node message = LLVMFunction.ilaver.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public void execute(int[] version) {
            NativeIntegerArray versionN = new NativeIntegerArray(version);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.ilaver.callName, null);
                }
                ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), versionN);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                versionN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DgeevNode extends DgeevNode {
        @Child private Node message = LLVMFunction.dgeev.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char jobVL, char jobVR, int n, double[] a, int lda, double[] wr, double[] wi, double[] vl, int ldvl, double[] vr, int ldvr, double[] work, int lwork) {
            // vl, vr may be null
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeDoubleArray wrN = new NativeDoubleArray(wr);
            NativeDoubleArray wiN = new NativeDoubleArray(wi);
            Object vlN = vl == null ? 0 : new NativeDoubleArray(vl);
            Object vrN = vr == null ? 0 : new NativeDoubleArray(vr);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dgeev.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), jobVL, jobVR, n, aN, lda,
                                wrN, wiN, vlN, ldvl, vrN, ldvr, workN, lwork);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                wrN.getValue();
                wiN.getValue();
                if (vl != null) {
                    ((NativeDoubleArray) vlN).getValue();
                }
                if (vr != null) {
                    ((NativeDoubleArray) vrN).getValue();
                }
                workN.getValue();
            }
        }
    }

    private static class TruffleLLVM_Dgeqp3Node extends Dgeqp3Node {
        @Child private Node message = LLVMFunction.dgeqp3.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(int m, int n, double[] a, int lda, int[] jpvt, double[] tau, double[] work, int lwork) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeIntegerArray jpvtN = new NativeIntegerArray(jpvt);
            NativeDoubleArray tauN = new NativeDoubleArray(tau);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dgeqp3.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), m, n, aN, lda, jpvtN,
                                tauN, workN, lwork);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                jpvtN.getValue();
                tauN.getValue();
                workN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DormqrNode extends DormqrNode {
        @Child private Node message = LLVMFunction.dormq.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char side, char trans, int m, int n, int k, double[] a, int lda, double[] tau, double[] c, int ldc, double[] work, int lwork) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeDoubleArray tauN = new NativeDoubleArray(tau);
            NativeDoubleArray cN = new NativeDoubleArray(c);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dormq.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), side, trans, m, n, k, aN, lda,
                                tauN, cN, ldc, workN, lwork);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                cN.getValue();
                workN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DtrtrsNode extends DtrtrsNode {
        @Child private Node message = LLVMFunction.dtrtrs.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char uplo, char trans, char diag, int n, int nrhs, double[] a, int lda, double[] b, int ldb) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeDoubleArray bN = new NativeDoubleArray(b);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dtrtrs.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), uplo, trans, diag, n, nrhs, aN, lda,
                                bN, ldb);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                bN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DgetrfNode extends DgetrfNode {
        @Child private Node message = LLVMFunction.dgetrf.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(int m, int n, double[] a, int lda, int[] ipiv) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeIntegerArray ipivN = new NativeIntegerArray(ipiv);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dgetrf.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), m, n, aN, lda, ipivN);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                ipivN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DpotrfNode extends DpotrfNode {
        @Child private Node message = LLVMFunction.dpotrf.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char uplo, int n, double[] a, int lda) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dpotrf.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), uplo, n, aN, lda);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DpotriNode extends DpotriNode {
        @Child private Node message = LLVMFunction.dpotri.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char uplo, int n, double[] a, int lda) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dpotri.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), uplo, n, aN, lda);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DpstrfNode extends DpstrfNode {
        @Child private Node message = LLVMFunction.dpstrf.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char uplo, int n, double[] a, int lda, int[] piv, int[] rank, double tol, double[] work) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeIntegerArray pivN = new NativeIntegerArray(piv);
            NativeIntegerArray rankN = new NativeIntegerArray(rank);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dpstrf.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), uplo, n, aN, lda,
                                pivN, rankN, tol, workN);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                pivN.getValue();
                rankN.getValue();
                workN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DgesvNode extends DgesvNode {
        @Child private Node message = LLVMFunction.dgesv.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(int n, int nrhs, double[] a, int lda, int[] ipiv, double[] b, int ldb) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeIntegerArray ipivN = new NativeIntegerArray(ipiv);
            NativeDoubleArray bN = new NativeDoubleArray(b);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dgesv.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), n, nrhs, aN, lda, ipivN, bN, ldb);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                ipivN.getValue();
                bN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DlangeNode extends DlangeNode {
        @Child private Node message = LLVMFunction.dlange.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public double execute(char norm, int m, int n, double[] a, int lda, double[] work) {
            // work may be null
            NativeDoubleArray aN = new NativeDoubleArray(a);
            Object workN = work == null ? 0 : new NativeDoubleArray(work);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dlange.callName, null);
                }
                return (double) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), norm, m, n, aN, lda, workN);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            }
        }
    }

    private static class TruffleLLVM_DgeconNode extends DgeconNode {
        @Child private Node message = LLVMFunction.dgecon.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char norm, int n, double[] a, int lda, double anorm, double[] rcond, double[] work, int[] iwork) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeDoubleArray rcondN = new NativeDoubleArray(rcond);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            NativeIntegerArray iworkN = new NativeIntegerArray(iwork);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dgecon.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), norm, n, aN, lda, anorm, rcondN, workN, iworkN);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                rcondN.getValue();
                workN.getValue();
                iworkN.getValue();
            }
        }
    }

    private static class TruffleLLVM_DsyevrNode extends DsyevrNode {
        @Child private Node message = LLVMFunction.dsyevr.createMessage();
        @CompilationFinal private SymbolHandle symbolHandle;

        @Override
        public int execute(char jobz, char range, char uplo, int n, double[] a, int lda, double vl, double vu, int il, int iu, double abstol, int[] m, double[] w, double[] z, int ldz, int[] isuppz,
                        double[] work, int lwork, int[] iwork, int liwork) {
            NativeDoubleArray aN = new NativeDoubleArray(a);
            NativeIntegerArray mN = new NativeIntegerArray(m);
            NativeDoubleArray wN = new NativeDoubleArray(w);
            Object zN = z == null ? 0 : new NativeDoubleArray(z);
            NativeIntegerArray isuppzN = new NativeIntegerArray(isuppz);
            NativeDoubleArray workN = new NativeDoubleArray(work);
            NativeIntegerArray iworkN = new NativeIntegerArray(iwork);
            try {
                if (symbolHandle == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    symbolHandle = DLL.findSymbol(LLVMFunction.dsyevr.callName, null);
                }
                return (int) ForeignAccess.sendExecute(message, symbolHandle.asTruffleObject(), jobz, range, uplo, n, aN,
                                lda, vl, vu, il, iu, abstol, mN, wN, zN, ldz,
                                isuppzN, workN, lwork, iworkN, liwork);
            } catch (InteropException e) {
                throw RInternalError.shouldNotReachHere(e);
            } finally {
                aN.getValue();
                mN.getValue();
                wN.getValue();
                if (z != null) {
                    ((NativeDoubleArray) zN).getValue();

                }
                isuppzN.getValue();
                workN.getValue();
                iworkN.getValue();
            }
        }
    }

    @Override
    public IlaverNode createIlaverNode() {
        return new TruffleLLVM_IlaverNode();
    }

    @Override
    public DgeevNode createDgeevNode() {
        return new TruffleLLVM_DgeevNode();
    }

    @Override
    public Dgeqp3Node createDgeqp3Node() {
        return new TruffleLLVM_Dgeqp3Node();
    }

    @Override
    public DormqrNode createDormqrNode() {
        return new TruffleLLVM_DormqrNode();
    }

    @Override
    public DtrtrsNode createDtrtrsNode() {
        return new TruffleLLVM_DtrtrsNode();
    }

    @Override
    public DgetrfNode createDgetrfNode() {
        return new TruffleLLVM_DgetrfNode();
    }

    @Override
    public DpotrfNode createDpotrfNode() {
        return new TruffleLLVM_DpotrfNode();
    }

    @Override
    public DpotriNode createDpotriNode() {
        return new TruffleLLVM_DpotriNode();
    }

    @Override
    public DpstrfNode createDpstrfNode() {
        return new TruffleLLVM_DpstrfNode();
    }

    @Override
    public DgesvNode createDgesvNode() {
        return new TruffleLLVM_DgesvNode();
    }

    @Override
    public DlangeNode createDlangeNode() {
        return new TruffleLLVM_DlangeNode();
    }

    @Override
    public DgeconNode createDgeconNode() {
        return new TruffleLLVM_DgeconNode();
    }

    @Override
    public DsyevrNode createDsyevrNode() {
        return new TruffleLLVM_DsyevrNode();
    }
}